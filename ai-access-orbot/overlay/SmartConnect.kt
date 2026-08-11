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

            // Before Tor exists, rotate only the PT process itself; reconfigure() requires conn != null.
            while (!startTransport(context, Prefs.transport)) {
                val failed = Prefs.transport
                val next = nextTransport() ?: run {
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
            notify("WATCHDOG_ARMED", "timeout=${Prefs.smartConnectTimeout}s transport=${Prefs.transport.id}")

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
                    notify("BOOTSTRAP_STALL", "progress=$progress transport=${old.id}; rotating transport")

                    if (!switchTransport(context, reconfigure, "bootstrap stalled at $progress%")) {
                        stopConnectionGuard()
                        try { Prefs.transport.stop() } catch (_: Throwable) {}
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

            // Critical upstream fix: every candidate gets a fresh bootstrap progress budget.
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
            // SQS is intentionally absent: upstream explicitly marks it unsupported.
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
        // Repeated identical values (the user's repeated 50%) do NOT extend the watchdog.
        if (newProgress != progress) {
            val old = progress
            progress = newProgress
            connectionAlive()
            notify("BOOTSTRAP_PROGRESS", "$old -> $newProgress transport=${Prefs.transport.id}")
        }
    }

    @JvmStatic
    fun cancel() {
        notify("WATCHDOG_CANCEL", "transport=${Prefs.transport.id} progress=$progress")
        stopConnectionGuard()
    }

    private fun connectionAlive() {
        connectionTimeout = TimeSource.Monotonic.markNow() + Prefs.smartConnectTimeout.seconds
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
