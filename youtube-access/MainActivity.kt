package io.github.romanvht.byedpi.activities

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import io.github.romanvht.byedpi.data.AppStatus
import io.github.romanvht.byedpi.data.FAILED_BROADCAST
import io.github.romanvht.byedpi.data.Mode
import io.github.romanvht.byedpi.data.SENDER
import io.github.romanvht.byedpi.data.STARTED_BROADCAST
import io.github.romanvht.byedpi.data.STOPPED_BROADCAST
import io.github.romanvht.byedpi.services.ServiceManager
import io.github.romanvht.byedpi.services.appStatus
import io.github.romanvht.byedpi.utility.getPreferences
import io.github.romanvht.byedpi.utility.getProxyIpAndPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "FreeVideo"
        private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        private const val VERSION = "0.2.0"

        private val BG_TOP = Color.rgb(5, 11, 28)
        private val BG_BOTTOM = Color.rgb(20, 6, 42)
        private val WHITE = Color.rgb(247, 249, 255)
        private val MUTED = Color.rgb(160, 169, 194)
        private val GLASS = Color.argb(185, 20, 20, 47)
    }

    private data class Strategy(val name: String, val args: String, val mode: String)

    private lateinit var contentRoot: FrameLayout
    private lateinit var statusText: TextView
    private lateinit var mainButton: Button
    private lateinit var strategies: List<Strategy>

    private var strategyIndex = 0
    private var attempts = 0
    private var verifyToken = 0
    private var userRequestedStop = false
    private var currentScreen = "home"

    private val vpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                setStatus("Подключение…")
                ServiceManager.start(this, Mode.VPN)
            } else {
                setStatus("Нужно разрешить локальное VPN-подключение")
                updateMainButton()
            }
        }

    private val saveLog =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.data ?: return@registerForActivityResult
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    contentResolver.openOutputStream(uri)?.use {
                        it.write(collectLogs().toByteArray())
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "log save failed", e)
                }
            }
        }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                STARTED_BROADCAST -> {
                    Log.i(TAG, "VPN started; route probe begins")
                    setStatus("Проверяю видеомаршрут…")
                    verifyRouteAndLaunch(launchAfterSuccess = currentScreen == "home")
                }

                STOPPED_BROADCAST -> {
                    if (userRequestedStop) setStatus("Готово")
                    updateMainButton()
                    if (currentScreen == "settings") showSettings()
                }

                FAILED_BROADCAST -> {
                    Log.e(TAG, "VPN FAILED sender=${intent.getIntExtra(SENDER, -1)}")
                    setStatus("Маршрут не запустился")
                    updateMainButton()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = BG_TOP

        prepareStrategyFiles()
        configureDefaults()
        buildShell()
        registerStatusReceiver()
        requestNotificationPermissionIfNeeded()

        val seenIntro = getPreferences().getBoolean("freevideo_intro_seen", false)
        if (seenIntro) showHome() else showIntro()

        Log.i(TAG, "FreeVideo v$VERSION started")
        Log.i(TAG, "Target package: $YOUTUBE_PACKAGE")
        Log.i(TAG, "Strategy[$strategyIndex]=${strategies[strategyIndex].name}: ${strategies[strategyIndex].args}")
    }

    override fun onResume() {
        super.onResume()
        if (::mainButton.isInitialized) updateMainButton()
        if (currentScreen == "settings" && ::contentRoot.isInitialized) showSettings()
    }

    override fun onDestroy() {
        verifyToken++
        try {
            unregisterReceiver(receiver)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    override fun onBackPressed() {
        when (currentScreen) {
            "settings", "other" -> showHome()
            else -> super.onBackPressed()
        }
    }

    private fun prepareStrategyFiles() {
        val googleVideo = File(filesDir, "freevideo_googlevideo_hosts.txt")
        googleVideo.writeText("googlevideo.com\n")

        val allYouTube = File(filesDir, "freevideo_youtube_hosts.txt")
        allYouTube.writeText(
            """
            googlevideo.com
            youtube.com
            youtu.be
            youtubei.googleapis.com
            i.ytimg.com
            yt3.ggpht.com
            yt4.ggpht.com
            googleapis.com
            ggpht.com
            googleusercontent.com
            """.trimIndent() + "\n"
        )

        val gv = googleVideo.absolutePath
        val all = allYouTube.absolutePath

        strategies = listOf(
            Strategy("GoogleVideo OOB / IPv4 / TCP", "-I 0.0.0.0 -U -H $gv -o1 -An", "TCP"),
            Strategy("GoogleVideo OOB+TLSREC / IPv4 / TCP", "-I 0.0.0.0 -U -H $gv -o1 -r-5+se -An", "TCP"),
            Strategy("YouTube OOB+TLSREC / IPv4 / TCP", "-I 0.0.0.0 -U -H $all -o1 -r-5+se -An", "TCP"),
            Strategy("YouTube adaptive TLSREC / IPv4 / TCP", "-I 0.0.0.0 -U -H $all -o1 -r-5+se -T3 -At -H $all -r1+s -An", "TCP"),
            Strategy("GoogleVideo split+disorder / IPv4 / TCP", "-I 0.0.0.0 -U -H $gv -s1+s -d3+s -An", "TCP"),
            Strategy("GoogleVideo QUIC fake + TLS OOB", "-I 0.0.0.0 -Ku -a11 -An -Kt -H $gv -o1 -r-5+se -An", "QUIC+TCP"),
            Strategy("YouTube QUIC fake + adaptive TLS", "-I 0.0.0.0 -Ku -a3 -An -Kt -H $all -s0 -o1 -d1 -r1+s -Ar -H $all -o1 -At -H $all -r1+s -An", "QUIC+TCP")
        )
    }

    private fun configureDefaults() {
        val p = getPreferences()
        val remembered = p.getString("fv_last_strategy_args", null)
        strategyIndex = strategies.indexOfFirst { it.args == remembered }.takeIf { it >= 0 } ?: 0

        p.edit {
            putString("byedpi_mode", "vpn")
            putString("dns_ip", "9.9.9.9")
            putBoolean("ipv6_enable", false)
            putBoolean("byedpi_enable_cmd_settings", true)
            putString("applist_type", "whitelist")
            putStringSet("selected_apps", setOf(YOUTUBE_PACKAGE))
            putString("byedpi_cmd_args", strategies[strategyIndex].args)
        }
    }

    private fun buildShell() {
        contentRoot = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(BG_TOP, Color.rgb(8, 7, 31), BG_BOTTOM)
            )
        }
        contentRoot.addView(NeonBackgroundView(this), FrameLayout.LayoutParams(-1, -1))
        setContentView(contentRoot)
    }

    private fun clearContent() {
        while (contentRoot.childCount > 1) contentRoot.removeViewAt(1)
    }

    private fun showIntro() {
        currentScreen = "intro"
        clearContent()
        val root = verticalRoot()
        addBrand(root, settings = false)

        root.addView(title("Стабилизация работы\nвидеохостингов", 31f, Gravity.CENTER).apply {
            setPadding(0, dp(42), 0, dp(12))
        }, lp(-1, -2))
        root.addView(body("Удобный доступ к видеосервисам\nпрямо в приложении.", 16f, Gravity.CENTER), lp(-1, -2))
        root.addView(body("Приложение не является удалённым VPN.", 12f, Gravity.CENTER).apply {
            setPadding(0, dp(12), 0, 0)
        }, lp(-1, -2))
        root.addView(VideoOrbView(this), LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = dp(18); bottomMargin = dp(6)
        })

        val badge = TextView(this).apply {
            text = "5 дней бесплатно"
            setTextColor(WHITE); textSize = 14f; gravity = Gravity.CENTER
            background = pillDrawable(Color.argb(140, 74, 39, 122), Color.argb(170, 155, 105, 255))
            setPadding(dp(22), dp(10), dp(22), dp(10))
        }
        root.addView(badge, lp(-2, -2).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(16) })
        root.addView(neonButton("ПРИСТУПИТЬ") {
            getPreferences().edit { putBoolean("freevideo_intro_seen", true) }
            showHome()
        }, lp(-1, dp(64)).apply { bottomMargin = dp(20) })
        contentRoot.addView(root, FrameLayout.LayoutParams(-1, -1))
    }

    private fun showHome() {
        currentScreen = "home"
        clearContent()
        val root = verticalRoot()
        addBrand(root, settings = true)

        root.addView(title("Открыть видеохостинг", 31f, Gravity.START).apply {
            setPadding(0, dp(38), 0, dp(8))
        }, lp(-1, -2))
        root.addView(body("Быстрый доступ к видео прямо в приложении", 15f, Gravity.START), lp(-1, -2))

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(20), dp(22), dp(20), dp(20)); background = glassDrawable()
        }
        card.addView(VideoOrbView(this), LinearLayout.LayoutParams(-1, dp(330)))
        card.addView(title("Видеохостинг", 24f, Gravity.CENTER), lp(-1, -2))
        card.addView(body("Видео", 14f, Gravity.CENTER).apply { setPadding(0, dp(4), 0, dp(4)) }, lp(-1, -2))
        statusText = body(if (appStatus.first == AppStatus.Running) "Маршрут активен" else "Готово", 13f, Gravity.CENTER)
        card.addView(statusText, lp(-1, -2))
        root.addView(card, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(26); bottomMargin = dp(22) })

        mainButton = neonButton("ОТКРЫТЬ") { onMainClick() }
        root.addView(mainButton, lp(-1, dp(64)).apply { bottomMargin = dp(10) })
        root.addView(TextView(this).apply {
            text = "Другие приложения"; setTextColor(MUTED); textSize = 14f; gravity = Gravity.CENTER
            setPadding(dp(12), dp(12), dp(12), dp(18)); setOnClickListener { showOtherApps() }
        }, lp(-1, -2))
        contentRoot.addView(root, FrameLayout.LayoutParams(-1, -1))
        updateMainButton()
    }

    private fun showSettings() {
        currentScreen = "settings"
        clearContent()
        val scroll = ScrollView(this)
        val root = verticalRoot()
        addBrand(root, settings = false, back = true)
        root.addView(title("Настройки", 31f, Gravity.START).apply { setPadding(0, dp(30), 0, dp(18)) }, lp(-1, -2))

        val stateCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(18)); background = glassDrawable()
        }
        stateCard.addView(settingsRow("Состояние", if (appStatus.first == AppStatus.Running) "Активно" else "Готово"))
        stateCard.addView(settingsRow("Видеохостинг", "YouTube"))
        stateCard.addView(settingsRow("Маршрут", "автоматически"))
        stateCard.addView(settingsRow("Транспорт", strategies[strategyIndex].mode))
        stateCard.addView(settingsRow("Стратегия", "${strategyIndex + 1}/${strategies.size}"))
        root.addView(stateCard, lp(-1, -2).apply { bottomMargin = dp(14) })

        root.addView(outlineButton("ПРОВЕРИТЬ МАРШРУТ") {
            if (appStatus.first == AppStatus.Running) {
                setStatus("Проверяю…"); verifyRouteAndLaunch(launchAfterSuccess = false)
            } else onMainClick()
        }, lp(-1, dp(56)).apply { bottomMargin = dp(10) })
        root.addView(outlineButton("СОХРАНИТЬ ЛОГ") { exportLog() }, lp(-1, dp(56)).apply { bottomMargin = dp(10) })

        if (appStatus.first == AppStatus.Running) {
            root.addView(outlineButton("ОТКЛЮЧИТЬ") {
                userRequestedStop = true; verifyToken++; ServiceManager.stop(this@MainActivity)
                Toast.makeText(this, "Маршрут отключается", Toast.LENGTH_SHORT).show()
            }, lp(-1, dp(56)).apply { bottomMargin = dp(18) })
        }

        val technical = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14)); background = glassDrawable()
        }
        technical.addView(title("Техническая информация", 15f, Gravity.START), lp(-1, -2))
        technical.addView(body(
            "FreeVideo v$VERSION\nByeByeDPI v1.7.7 / HEV\nIPv4-first: включено\nYouTube-only: включено\nТекущая стратегия: ${strategies[strategyIndex].name}",
            12f, Gravity.START
        ).apply { setPadding(0, dp(8), 0, 0) }, lp(-1, -2))
        root.addView(technical, lp(-1, -2).apply { bottomMargin = dp(24) })
        scroll.addView(root)
        contentRoot.addView(scroll, FrameLayout.LayoutParams(-1, -1))
    }

    private fun showOtherApps() {
        currentScreen = "other"
        clearContent()
        val root = verticalRoot()
        addBrand(root, settings = false, back = true)
        root.addView(title("Другие приложения", 31f, Gravity.START).apply { setPadding(0, dp(34), 0, dp(18)) }, lp(-1, -2))
        root.addView(productCard("Нейросети", "Стабилизация работы AI-сервисов"))
        root.addView(productCard("Мессенджеры", "Стабилизация работы мессенджеров"))
        contentRoot.addView(root, FrameLayout.LayoutParams(-1, -1))
    }

    private fun productCard(name: String, subtitle: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(18)); background = glassDrawable()
        addView(title(name, 23f, Gravity.START), lp(-1, -2))
        addView(body(subtitle, 14f, Gravity.START).apply { setPadding(0, dp(7), 0, dp(14)) }, lp(-1, -2))
        addView(outlineButton("ПОДРОБНЕЕ") {
            Toast.makeText(this@MainActivity, "Раздел будет добавлен отдельно", Toast.LENGTH_SHORT).show()
        }, lp(-1, dp(50)))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) }
    }

    private fun onMainClick() {
        userRequestedStop = false
        if (!isYouTubeInstalled()) {
            setStatus("YouTube не установлен")
            Toast.makeText(this, "Установите официальное приложение YouTube", Toast.LENGTH_LONG).show()
            return
        }
        if (appStatus.first == AppStatus.Running) {
            setStatus("Проверяю маршрут…"); verifyRouteAndLaunch(launchAfterSuccess = true); return
        }
        attempts = 0; verifyToken++; applyStrategy(strategyIndex)
        val prep = VpnService.prepare(this)
        if (prep != null) vpnPermission.launch(prep) else {
            setStatus("Подключение…"); ServiceManager.start(this, Mode.VPN)
        }
        updateMainButton()
    }

    private fun verifyRouteAndLaunch(launchAfterSuccess: Boolean) {
        val token = ++verifyToken
        val current = strategies[strategyIndex]
        Log.i(TAG, "Probe strategy ${strategyIndex + 1}/${strategies.size}: ${current.name} -> ${current.args}")
        lifecycleScope.launch(Dispatchers.IO) {
            val result = probeVideoRoute()
            withContext(Dispatchers.Main) {
                if (token != verifyToken || userRequestedStop) return@withContext
                if (result) {
                    attempts = 0
                    getPreferences().edit { putString("fv_last_strategy_args", current.args); putString("fv_last_strategy_name", current.name) }
                    Log.i(TAG, "ROUTE_OK strategy=${current.name}")
                    setStatus("Маршрут готов"); updateMainButton()
                    if (launchAfterSuccess) launchYouTube()
                } else {
                    attempts++
                    Log.w(TAG, "ROUTE_FAIL strategy=${current.name}; attempt=$attempts")
                    if (attempts >= strategies.size) {
                        setStatus("Автоподбор не нашёл маршрут"); updateMainButton()
                        Toast.makeText(this@MainActivity, "Сохраните лог — все стратегии проверены", Toast.LENGTH_LONG).show()
                        return@withContext
                    }
                    strategyIndex = (strategyIndex + 1) % strategies.size
                    applyStrategy(strategyIndex)
                    setStatus("Подбираю маршрут ${strategyIndex + 1}/${strategies.size}…")
                    ServiceManager.restart(this@MainActivity, Mode.VPN)
                }
            }
        }
    }

    private fun applyStrategy(index: Int) {
        val s = strategies[index]
        getPreferences().edit { putString("byedpi_cmd_args", s.args) }
        Log.i(TAG, "APPLY strategy[$index]=${s.name}: ${s.args}")
    }

    private fun probeVideoRoute(): Boolean {
        val (_, portText) = getPreferences().getProxyIpAndPort()
        val port = portText.toIntOrNull() ?: 1080
        val socks = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port))
        val targets = listOf("https://www.youtube.com/generate_204", "https://redirector.googlevideo.com/report_mapping")

        for ((idx, target) in targets.withIndex()) {
            var conn: HttpsURLConnection? = null
            try {
                conn = URL(target).openConnection(socks) as HttpsURLConnection
                conn.connectTimeout = if (idx == 0) 4500 else 6500
                conn.readTimeout = if (idx == 0) 4500 else 6500
                conn.instanceFollowRedirects = true; conn.requestMethod = "GET"; conn.useCaches = false
                conn.setRequestProperty("Connection", "close")
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 Android FreeVideo/$VERSION")
                val code = conn.responseCode
                Log.i(TAG, "PROBE $target -> HTTP $code")
                if (code !in 200..399) return false
                if (idx == 1) {
                    val sample = try {
                        conn.inputStream.use { input ->
                            val b = ByteArray(256); val n = input.read(b); if (n > 0) String(b, 0, n) else ""
                        }
                    } catch (_: Exception) { "" }
                    Log.i(TAG, "PROBE googlevideo bytes=${sample.length}")
                    if (sample.isBlank()) return false
                }
            } catch (e: Exception) {
                Log.w(TAG, "PROBE_FAIL $target: ${e.javaClass.simpleName}: ${e.message}")
                return false
            } finally { conn?.disconnect() }
        }
        return true
    }

    private fun launchYouTube() {
        val launch = packageManager.getLaunchIntentForPackage(YOUTUBE_PACKAGE)
        if (launch == null) { setStatus("YouTube не установлен"); return }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        Log.i(TAG, "Launching official YouTube")
        startActivity(launch)
    }

    private fun isYouTubeInstalled(): Boolean = try {
        packageManager.getPackageInfo(YOUTUBE_PACKAGE, 0); true
    } catch (_: PackageManager.NameNotFoundException) { false }

    private fun updateMainButton() {
        if (!::mainButton.isInitialized) return
        mainButton.isEnabled = true; mainButton.alpha = 1f; mainButton.text = "ОТКРЫТЬ"
    }

    private fun setStatus(text: String) {
        Log.i(TAG, "STATUS $text")
        if (::statusText.isInitialized) statusText.text = text
    }

    private fun exportLog() {
        saveLog.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "freevideo_v${VERSION}_route.log")
        })
    }

    private fun collectLogs(): String = try {
        Runtime.getRuntime().exec("logcat -d -v threadtime").inputStream.bufferedReader().use { it.readText() }
    } catch (e: Exception) { "Failed to collect logcat: ${e.javaClass.name}: ${e.message}" }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerStatusReceiver() {
        val f = IntentFilter().apply { addAction(STARTED_BROADCAST); addAction(STOPPED_BROADCAST); addAction(FAILED_BROADCAST) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) registerReceiver(receiver, f, RECEIVER_NOT_EXPORTED)
        else registerReceiver(receiver, f)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 41)
        }
    }

    private fun verticalRoot(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(24), dp(30), dp(24), dp(12))
    }

    private fun addBrand(root: LinearLayout, settings: Boolean, back: Boolean = false) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        if (back) {
            row.addView(TextView(this).apply {
                text = "‹"; setTextColor(WHITE); textSize = 38f; gravity = Gravity.CENTER
                setPadding(0, 0, dp(16), 0); setOnClickListener { showHome() }
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
        row.addView(MiniVideoGlyphView(this), LinearLayout.LayoutParams(dp(38), dp(38)).apply { rightMargin = dp(10) })
        row.addView(TextView(this).apply {
            text = "FreeVideo"; setTextColor(WHITE); textSize = 22f; typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, -2, 1f))
        if (settings) {
            row.addView(TextView(this).apply {
                text = "⚙"; setTextColor(Color.rgb(201, 198, 255)); textSize = 27f; gravity = Gravity.CENTER
                setPadding(dp(10), dp(6), dp(6), dp(6)); setOnClickListener { showSettings() }
            }, LinearLayout.LayoutParams(dp(54), dp(48)))
        }
        root.addView(row, lp(-1, dp(48)))
    }

    private fun title(text: String, size: Float, gravityValue: Int): TextView = TextView(this).apply {
        this.text = text; setTextColor(WHITE); textSize = size
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); gravity = gravityValue; includeFontPadding = false
    }

    private fun body(text: String, size: Float, gravityValue: Int): TextView = TextView(this).apply {
        this.text = text; setTextColor(MUTED); textSize = size; gravity = gravityValue
        includeFontPadding = false; setLineSpacing(0f, 1.16f)
    }

    private fun settingsRow(left: String, right: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, dp(8))
        addView(TextView(this@MainActivity).apply { text = left; setTextColor(MUTED); textSize = 14f }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(TextView(this@MainActivity).apply { text = right; setTextColor(WHITE); textSize = 14f; gravity = Gravity.END }, LinearLayout.LayoutParams(0, -2, 1f))
    }

    private fun neonButton(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text; setTextColor(WHITE); textSize = 16f; typeface = Typeface.DEFAULT_BOLD; isAllCaps = false; stateListAnimator = null
        background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.rgb(49, 125, 255), Color.rgb(113, 62, 255), Color.rgb(186, 56, 255))).apply { cornerRadius = dp(22).toFloat() }
        setOnClickListener { onClick() }
    }

    private fun outlineButton(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text; setTextColor(WHITE); textSize = 14f; typeface = Typeface.DEFAULT_BOLD; isAllCaps = false; stateListAnimator = null
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; setColor(Color.argb(115, 23, 23, 55)); cornerRadius = dp(18).toFloat()
            setStroke(dp(1), Color.argb(170, 112, 93, 255))
        }
        setOnClickListener { onClick() }
    }

    private fun glassDrawable(): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(Color.argb(205, 26, 22, 61), GLASS, Color.argb(195, 11, 29, 56))
    ).apply { cornerRadius = dp(28).toFloat(); setStroke(dp(1), Color.argb(145, 114, 99, 255)) }

    private fun pillDrawable(fill: Int, stroke: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; setColor(fill); cornerRadius = dp(30).toFloat(); setStroke(dp(1), stroke)
    }

    private fun lp(w: Int, h: Int): LinearLayout.LayoutParams = LinearLayout.LayoutParams(w, h)
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}

private class NeonBackgroundView(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        fun glow(cx: Float, cy: Float, radius: Float, color: Int) {
            p.shader = RadialGradient(cx, cy, radius,
                intArrayOf(Color.argb(82, Color.red(color), Color.green(color), Color.blue(color)), Color.TRANSPARENT),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, radius, p); p.shader = null
        }
        glow(w * .15f, h * .18f, w * .62f, Color.rgb(53, 84, 255))
        glow(w * .88f, h * .42f, w * .52f, Color.rgb(155, 55, 255))
        glow(w * .42f, h * .92f, w * .60f, Color.rgb(23, 140, 255))
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.2f; p.color = Color.argb(30, 145, 125, 255)
        for (i in 0..6) {
            val yy = h * (.18f + i * .11f); val path = Path(); path.moveTo(-w * .1f, yy)
            path.cubicTo(w * .22f, yy - h * .08f, w * .65f, yy + h * .08f, w * 1.1f, yy - h * .03f)
            canvas.drawPath(path, p)
        }
        p.style = Paint.Style.FILL
    }
}

private class MiniVideoGlyphView(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        p.color = Color.rgb(113, 83, 255); p.style = Paint.Style.FILL
        canvas.drawRoundRect(w * .06f, h * .20f, w * .70f, h * .80f, h * .16f, h * .16f, p)
        val cam = Path().apply { moveTo(w*.68f,h*.34f); lineTo(w*.95f,h*.20f); lineTo(w*.95f,h*.80f); lineTo(w*.68f,h*.66f); close() }
        p.color = Color.rgb(51, 202, 255); canvas.drawPath(cam, p)
        val play = Path().apply { moveTo(w*.29f,h*.35f); lineTo(w*.29f,h*.65f); lineTo(w*.52f,h*.50f); close() }
        p.color = Color.WHITE; canvas.drawPath(play, p)
    }
}

private class VideoOrbView(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f; val cy = height / 2f; val r = minOf(width, height) * .34f
        p.shader = RadialGradient(cx, cy, r * 1.65f,
            intArrayOf(Color.argb(210,76,85,255), Color.argb(150,134,52,255), Color.argb(60,32,201,255), Color.TRANSPARENT),
            floatArrayOf(0f,.48f,.76f,1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r * 1.65f, p); p.shader = null
        stroke.strokeWidth = 3f
        for (i in 0..7) { stroke.color = Color.argb(28+i*5,94,145,255); canvas.drawCircle(cx, cy, r*(1f+i*.08f), stroke) }
        val body = RectF(cx-r*.74f, cy-r*.48f, cx+r*.45f, cy+r*.48f)
        p.shader = LinearGradient(body.left,body.top,body.right,body.bottom,
            intArrayOf(Color.rgb(48,167,255),Color.rgb(114,65,255),Color.rgb(193,51,255)),null,Shader.TileMode.CLAMP)
        canvas.drawRoundRect(body,r*.18f,r*.18f,p); p.shader = null
        val cam = Path().apply { moveTo(body.right-r*.02f,cy-r*.26f); lineTo(cx+r*.85f,cy-r*.43f); lineTo(cx+r*.85f,cy+r*.43f); lineTo(body.right-r*.02f,cy+r*.26f); close() }
        p.color = Color.rgb(88,204,255); canvas.drawPath(cam,p)
        val play = Path().apply { moveTo(cx-r*.28f,cy-r*.24f); lineTo(cx-r*.28f,cy+r*.24f); lineTo(cx+r*.13f,cy); close() }
        p.color = Color.WHITE; canvas.drawPath(play,p)
        stroke.strokeWidth = r*.03f; stroke.color = Color.argb(155,86,225,255)
        canvas.drawArc(RectF(cx-r*1.12f,cy-r*1.12f,cx+r*1.12f,cy+r*1.12f),-25f,135f,false,stroke)
        stroke.color = Color.argb(145,194,87,255)
        canvas.drawArc(RectF(cx-r*1.25f,cy-r*1.25f,cx+r*1.25f,cy+r*1.25f),145f,120f,false,stroke)
    }
}
