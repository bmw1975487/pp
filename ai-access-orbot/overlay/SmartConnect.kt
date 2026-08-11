package org.torproject.android.service.circumvention

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.torproject.android.util.Prefs
import java.util.LinkedHashSet
import java.util.Timer
import java.util.TimerTask
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** AI Access One hardened SmartConnect with explicit RU MOAT and anti-stall rotation. */
object SmartConnect {
    const val AI_ACTION = "org.torproject.android.ai.SMARTCONNECT"
    const val EXTRA_EVENT = "event"
    const val EXTRA_DETAIL = "detail"

    private val ioScope: CoroutineScope by lazy { CoroutineScope(Dispatchers.IO) }
    private val mainScope: CoroutineScope by lazy { CoroutineScope(Dispatchers.Main) }

    private var progress = 0
    private var connectionTimeout = TimeSource.Monotonic.markNow()
    private var connectionGuard: Timer? = null
    private var appContext: Context? = null
    private val attempted = LinkedHashSet<Transport>()

    private val fallbackOrder = listOf(
        Transport.SNOWFLAKE,
        Transport.SNOWFLAKE_AMP,
        Transport.WEBTUNNEL,
        Transport.DNSTT,
        Transport.CUSTOM,
        Transport.OBFS4,
        Transport.NONE
    )

    @JvmStatic
    fun handle(
        context: Context,
        startTor: () -> Exception?,
        reconfigure: () -> Boolean,
        stopTor: (e: Exception?) -> Unit,
        completed: () -> Unit
    ) {
        appContext = context.applicationContext
        progress = 0
        attempted.clear()
        stopConnectionGuard()

        if (!Prefs.smartConnect) {
            val exception = startTor()
            return if (exception != null) stopTor(exception) else completed()
        }

        ioScope.launch {
            val country = Prefs.bridgeCountry?.takeIf { it.isNotBlank() }
            var conf: Pair<Transport, List<String>>? = null
            try {
                conf = AutoConf.`do`(context, country, true)
                notify("AUTOCONF_OK", "country=${country ?: "auto"} transport=${conf?.first?.id ?: "none"} bridges=${conf?.second?.size ?: 0}")
            } catch (t: Throwable) {
                notify("AUTOCONF_FAIL", "${t.javaClass.simpleName}: ${t.message ?: ""}")
            }

            conf?.second?.let { if (it.isNotEmpty()) Prefs.bridgesList = it }
            Prefs.transport = conf?.first ?: Transport.SNOWFLAKE
            attempted.add(Prefs.transport)

            while (!startTransport(context, Prefs.transport)) {
                val failed = Prefs.transport
                val next = nextTransport() ?: run {
                    notify("TRANSPORT_EXHAUSTED", "before Tor start; attempted=${attempted.joinToString(",") { it.id }}")
                    mainScope.launch { stopTor(Exception("Smart Connect: no transport could start")) }
                    return@launch
                }
                attempted.add(next)
                Prefs.transport = next
                progress = 0
                notify("TRANSPORT_SWITCH", "${failed.id} -> ${next.id}; reason=PT start failed")
            }

            val exception = startTor()
            if (exception != null) {
                mainScope.launch { stopTor(exception) }
                return@launch
            }

            connectionAlive()
            notify("WATCHDOG_ARMED", "timeout=${stallSeconds()}s transport=${Prefs.transport.id}")

            connectionGuard = Timer("AiSmartConnectGuard", true)
            connectionGuard?.schedule(object : TimerTask() {
                override fun run() {
                    if (progress >= 100) {
                        notify("BOOTSTRAP_COMPLETE", "transport=${Prefs.transport.id}")
                        stopConnectionGuard()
                        Prefs.smartConnect = false
                        mainScope.launch { completed() }
                        return
                    }

                    if (TimeSource.Monotonic.markNow() < connectionTimeout) return

                    val old = Prefs.transport
                    notify("BOOTSTRAP_STALL", "progress=$progress transport=${old.id}; timeout=${stallSeconds()}s; rotating transport")

                    if (!switchTransport(context, reconfigure, "bootstrap stalled at $progress%")) {
                        stopConnectionGuard()
                        try { Prefs.transport.stop() } catch (_: Throwable) {}
                        notify("TRANSPORT_EXHAUSTED", "bootstrap=$progress attempted=${attempted.joinToString(",") { it.id }}")
                        mainScope.launch { stopTor(Exception("Smart Connect exhausted transports at bootstrap $progress%")) }
                    }
                }
            }, 1000, 1000)
        }
    }

    /** Called only after Tor/control connection exists. Keeps trying candidates until reconfigure succeeds. */
    private fun switchTransport(context: Context, reconfigure: () -> Boolean, reason: String): Boolean {
        try { Prefs.transport.stop() } catch (_: Throwable) {}

        while (true) {
            val previous = Prefs.transport
            val next = nextTransport() ?: return false
            attempted.add(next)
            Prefs.transport = next

            progress = 0
            connectionAlive()
            notify("TRANSPORT_SWITCH", "${previous.id} -> ${next.id}; reason=$reason; attempted=${attempted.joinToString(",") { it.id }}")

            if (!startTransport(context, next)) continue

            try {
                if (reconfigure()) {
                    notify("TRANSPORT_ACTIVE", "transport=${next.id}")
                    return true
                }
                notify("RECONFIGURE_FAIL", "transport=${next.id}; trying next")
            } catch (t: Throwable) {
                notify("RECONFIGURE_EXCEPTION", "transport=${next.id} ${t.javaClass.simpleName}: ${t.message ?: ""}")
            }

            try { next.stop() } catch (_: Throwable) {}
        }
    }

    private fun nextTransport(): Transport? {
        for (candidate in fallbackOrder) {
            if (attempted.contains(candidate)) continue
            if (candidate == Transport.CUSTOM && Prefs.bridgesList.isEmpty()) continue
            return candidate
        }
        return null
    }

    private fun startTransport(context: Context, transport: Transport): Boolean {
        return try {
            transport.start(context)
            notify("TRANSPORT_START", "transport=${transport.id} port=${transport.port}")
            true
        } catch (t: Throwable) {
            notify("TRANSPORT_START_FAIL", "transport=${transport.id} ${t.javaClass.simpleName}: ${t.message ?: ""}")
            false
        }
    }

    @JvmStatic
    fun updateProgress(newProgress: Int) {
        if (!Prefs.smartConnect) return
        // Never let stale/out-of-order bootstrap notices move progress backwards.
        // After a transport switch progress is reset to 0, so the new transport can still
        // report the current Tor phase (for example 30) and receive a fresh watchdog budget.
        if (newProgress > progress) {
            val old = progress
            progress = newProgress
            connectionAlive()
            notify("BOOTSTRAP_PROGRESS", "$old -> $newProgress transport=${Prefs.transport.id} timeout=${stallSeconds()}s")
        }
    }

    @JvmStatic
    fun cancel() {
        notify("WATCHDOG_CANCEL", "transport=${Prefs.transport.id} progress=$progress")
        stopConnectionGuard()
    }

    /**
     * Consensus and descriptor loading legitimately need longer than the first handshake.
     * v0.2.3 used 15s for everything, which rotated transports while a valid bridge was
     * still downloading the consensus. This remains bounded: no phase can wait forever.
     */
    private fun stallSeconds(): Int = when {
        progress < 10 -> 20
        progress < 25 -> 30
        progress < 75 -> 50
        else -> 35
    }

    private fun connectionAlive() {
        connectionTimeout = TimeSource.Monotonic.markNow() + stallSeconds().seconds
    }

    private fun stopConnectionGuard() {
        connectionGuard?.cancel()
        connectionGuard?.purge()
        connectionGuard = null
    }

    private fun notify(event: String, detail: String) {
        val c = appContext ?: return
        try {
            c.sendBroadcast(
                Intent(AI_ACTION)
                    .setPackage(c.packageName)
                    .putExtra(EXTRA_EVENT, event)
                    .putExtra(EXTRA_DETAIL, detail)
            )
        } catch (_: Throwable) {}
    }
}
