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

/**
 * AI Access One hardened SmartConnect.
 *
 * Differences from upstream:
 *  - country is passed explicitly to AutoConf, enabling the GP DNSTT MOAT path;
 *  - cannotConnectWithoutPt=true for the RU test environment;
 *  - transport watchdog is authoritative: a transport that makes no bootstrap progress
 *    inside Prefs.smartConnectTimeout is abandoned;
 *  - bootstrap progress is reset on every transport switch;
 *  - fallback covers Snowflake, AMP, WebTunnel, DNSTT, MOAT custom bridges, obfs4 and direct;
 *  - every recovery decision is broadcast to the one-button UI/log.
 */
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
                // Explicit country enables Guardian Project's DNSTT-backed MOAT endpoint.
                // The third argument prevents AutoConf from treating direct Tor as acceptable
                // in an environment where we already know direct access is unreliable.
                conf = AutoConf.`do`(context, country, true)
                notify("AUTOCONF_OK", "country=${country ?: "auto"} transport=${conf?.first?.id ?: "none"} bridges=${conf?.second?.size ?: 0}")
            } catch (t: Throwable) {
                notify("AUTOCONF_FAIL", "${t.javaClass.simpleName}: ${t.message ?: ""}")
            }

            conf?.second?.let {
                if (it.isNotEmpty()) Prefs.bridgesList = it
            }

            Prefs.transport = conf?.first ?: Transport.SNOWFLAKE
            attempted.add(Prefs.transport)

            if (!startTransport(context, Prefs.transport)) {
                if (!switchTransport(context, reconfigure, "initial transport start failed")) {
                    mainScope.launch { stopTor(Exception("Smart Connect: no transport could start")) }
                    return@launch
                }
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

    private fun switchTransport(context: Context, reconfigure: () -> Boolean, reason: String): Boolean {
        val previous = Prefs.transport
        try { previous.stop() } catch (_: Throwable) {}

        while (true) {
            val next = nextTransport() ?: return false
            attempted.add(next)
            Prefs.transport = next

            // Critical: each transport starts with a fresh bootstrap progress budget.
            progress = 0
            connectionAlive()
            notify("TRANSPORT_SWITCH", "${previous.id} -> ${next.id}; reason=$reason; attempted=${attempted.joinToString(",") { it.id }}")

            if (!startTransport(context, next)) continue

            return try {
                if (reconfigure()) {
                    notify("TRANSPORT_ACTIVE", "transport=${next.id}")
                    true
                } else {
                    notify("RECONFIGURE_FAIL", "transport=${next.id}")
                    try { next.stop() } catch (_: Throwable) {}
                    false
                }
            } catch (t: Throwable) {
                notify("RECONFIGURE_EXCEPTION", "transport=${next.id} ${t.javaClass.simpleName}: ${t.message ?: ""}")
                try { next.stop() } catch (_: Throwable) {}
                false
            }
        }
    }

    private fun nextTransport(): Transport? {
        for (candidate in fallbackOrder) {
            if (attempted.contains(candidate)) continue
            if (candidate == Transport.CUSTOM && Prefs.bridgesList.isEmpty()) continue
            // Snowflake SQS is intentionally excluded: upstream marks it unsupported.
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
