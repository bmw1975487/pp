package org.torproject.android

import android.content.Context
import org.torproject.android.util.Prefs

object AiAccessPrefs {
    const val CHATGPT_PACKAGE = "com.openai.chatgpt"

    @JvmStatic
    fun configure(context: Context, exitCountry: String = "nl") {
        Prefs.setContext(context.applicationContext)
        Prefs.putUseVpn(true)
        Prefs.torifiedApps = CHATGPT_PACKAGE
        Prefs.smartConnect = true
        // Base timeout. SmartConnect v0.2.4 uses a longer adaptive window during
        // consensus/descriptor loading so it does not rotate a working transport too early.
        Prefs.smartConnectTimeout = 25
        // Physical target network is Russia. Explicit country also enables the
        // Guardian Project DNSTT-backed MOAT control path in AutoConf.
        Prefs.bridgeCountry = "ru"
        // Critical v0.2.4 fix: never pin/change an exit before Tor reaches 100%.
        // AiAccessActivity applies NL/DE/FR/GB/US only after bootstrap completes.
        Prefs.exitNodes = ""
    }

    @JvmStatic
    fun selectedTransport(): String = Prefs.transport.id

    @JvmStatic
    fun selectedExit(): String = Prefs.exitNodes ?: ""

    @JvmStatic
    fun smartConnectEnabled(): Boolean = Prefs.smartConnect
}
