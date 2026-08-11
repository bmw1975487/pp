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
        Prefs.smartConnectTimeout = 25
        Prefs.bridgeCountry = "ru"
        Prefs.exitNodes = "{$exitCountry}"
    }

    @JvmStatic
    fun selectedTransport(): String = Prefs.transport.id

    @JvmStatic
    fun selectedExit(): String = Prefs.exitNodes ?: ""

    @JvmStatic
    fun smartConnectEnabled(): Boolean = Prefs.smartConnect
}
