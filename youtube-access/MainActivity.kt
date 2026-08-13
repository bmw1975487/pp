package io.github.romanvht.byedpi.activities

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import io.github.romanvht.byedpi.data.AppStatus
import io.github.romanvht.byedpi.data.FAILED_BROADCAST
import io.github.romanvht.byedpi.data.Mode
import io.github.romanvht.byedpi.data.STARTED_BROADCAST
import io.github.romanvht.byedpi.data.STOPPED_BROADCAST
import io.github.romanvht.byedpi.services.ServiceManager
import io.github.romanvht.byedpi.services.appStatus
import io.github.romanvht.byedpi.utility.getPreferences
import io.github.romanvht.byedpi.utility.getProxyIpAndPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
        private const val VERSION = "0.2.1"
        private const val SCREEN_INTRO = 2
        private const val SCREEN_MAIN = 3
        private const val SCREEN_PAY = 4
        private const val SCREEN_SETTINGS = 5
        private const val SCREEN_OTHER = 6

        private val STRATEGY_TEMPLATES = listOf(
            "-I 0.0.0.0 -U -H {GV} -o1 -An",
            "-I 0.0.0.0 -U -H {GV} -o1 -r-5+se -An",
            "-I 0.0.0.0 -U -H {ALL} -o1 -r-5+se -An",
            "-I 0.0.0.0 -U -H {GV} -s1+s -d3+s -An",
            "-I 0.0.0.0 -U -H {GV} -f-1 -t8 -s1+s -An",
            "-I 0.0.0.0 -Ku -a11 -An -Kt -H {GV} -o1 -r-5+se -An",
            "-I 0.0.0.0 -Ku -a3 -An -Kt -H {ALL} -o1 -r-5+se -An"
        )
    }

    private lateinit var root: FrameLayout
    private lateinit var screenView: ImageView
    private var screen = SCREEN_INTRO
    private var strategyIndex = 0
    private var attempts = 0
    private var probeGeneration = 0
    private var pendingLaunch = false
    private var strategies: List<String> = emptyList()

    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            Log.i(TAG, "VPN_PERMISSION_OK")
            startVpnForYouTube()
        } else {
            pendingLaunch = false
            Log.w(TAG, "VPN_PERMISSION_DENIED")
            Toast.makeText(this, "Нужно разрешить VPN-подключение", Toast.LENGTH_LONG).show()
        }
    }

    private val saveLog = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@registerForActivityResult
        lifecycleScope.launch(Dispatchers.IO) {
            val text = collectLogs()
            try {
                contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            } catch (e: IOException) {
                Log.e(TAG, "LOG_SAVE_FAIL", e)
            }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                STARTED_BROADCAST -> {
                    Log.i(TAG, "VPN_STARTED strategy=${strategyIndex + 1}/${strategies.size}")
                    if (pendingLaunch) {
                        pendingLaunch = false
                        launchYouTubeNow()
                    }
                    verifyInBackground()
                }
                STOPPED_BROADCAST -> Log.i(TAG, "VPN_STOPPED")
                FAILED_BROADCAST -> {
                    Log.e(TAG, "VPN_FAILED")
                    if (pendingLaunch) {
                        pendingLaunch = false
                        launchYouTubeNow()
                    }
                    Toast.makeText(this@MainActivity, "Маршрут не поднялся. Сохраните лог.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemUi()
        prepareStrategies()
        configureDefaults()
        buildImageUi()
        registerStatusReceiver()
        requestNotificationPermissionIfNeeded()

        val seen = getPreferences().getBoolean("freevideo_exact_intro_seen", false)
        showScreen(if (seen) SCREEN_MAIN else SCREEN_INTRO)
        Log.i(TAG, "APP_START version=$VERSION target=$YOUTUBE_PACKAGE exactJpgUi=true")
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
    }

    override fun onDestroy() {
        probeGeneration++
        try { unregisterReceiver(receiver) } catch (_: Throwable) {}
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when (screen) {
            SCREEN_SETTINGS, SCREEN_OTHER, SCREEN_PAY -> showScreen(SCREEN_MAIN)
            SCREEN_INTRO -> super.onBackPressed()
            else -> super.onBackPressed()
        }
    }

    private fun hideSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
        }
    }

    private fun buildImageUi() {
        root = FrameLayout(this).apply { setBackgroundColor(android.graphics.Color.BLACK) }
        screenView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(android.graphics.Color.BLACK)
            isClickable = true
            isFocusable = true
            setOnTouchListener { _, e -> handleTouch(e) }
            setOnLongClickListener {
                if (screen == SCREEN_MAIN) {
                    showScreen(SCREEN_SETTINGS)
                    true
                } else false
            }
        }
        root.addView(screenView, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
    }

    private fun showScreen(which: Int) {
        screen = which
        val asset = when (which) {
            SCREEN_INTRO -> "freevideo/02_FreeVideo_start.jpg"
            SCREEN_MAIN -> "freevideo/03_FreeVideo_videohosting.jpg"
            SCREEN_PAY -> "freevideo/04_FreeVideo_99rub.jpg"
            SCREEN_SETTINGS -> "freevideo/05_FreeVideo_settings.jpg"
            SCREEN_OTHER -> "freevideo/06_FreeVideo_other_apps.jpg"
            else -> "freevideo/03_FreeVideo_videohosting.jpg"
        }
        try {
            assets.open(asset).use { input ->
                val bitmap = android.graphics.BitmapFactory.decodeStream(input)
                screenView.setImageBitmap(bitmap)
            }
            Log.i(TAG, "UI_SCREEN screen=$which asset=$asset")
        } catch (t: Throwable) {
            Log.e(TAG, "UI_ASSET_FAIL asset=$asset", t)
            screenView.setImageDrawable(null)
            Toast.makeText(this, "Ошибка экрана FreeVideo", Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun handleTouch(e: MotionEvent): Boolean {
        if (e.action != MotionEvent.ACTION_UP) return true
        val p = imagePoint(e.x, e.y) ?: return true
        val x = p.first
        val y = p.second
        when (screen) {
            SCREEN_INTRO -> {
                if (y > 0.78f) {
                    getPreferences().edit { putBoolean("freevideo_exact_intro_seen", true) }
                    showScreen(SCREEN_MAIN)
                }
            }
            SCREEN_MAIN -> {
                when {
                    y > 0.78f -> onOpenPressed()
                    x > 0.72f && y < 0.22f -> showScreen(SCREEN_SETTINGS)
                    x < 0.28f && y < 0.22f -> showScreen(SCREEN_OTHER)
                }
            }
            SCREEN_PAY -> {
                when {
                    y > 0.78f && y < 0.93f -> Toast.makeText(this, "Оплата будет подключена после сетевого теста", Toast.LENGTH_LONG).show()
                    y >= 0.93f -> showScreen(SCREEN_MAIN)
                }
            }
            SCREEN_SETTINGS -> {
                when {
                    x < 0.20f && y < 0.14f -> showScreen(SCREEN_MAIN)
                    y in 0.36f..0.49f -> if (appStatus.first == AppStatus.Running) verifyInBackground() else onOpenPressed()
                    y in 0.49f..0.69f -> exportLog()
                    y in 0.69f..0.82f -> exportLog()
                }
            }
            SCREEN_OTHER -> if (x < 0.22f && y < 0.20f) showScreen(SCREEN_MAIN)
        }
        return true
    }

    private fun imagePoint(viewX: Float, viewY: Float): Pair<Float, Float>? {
        val d = screenView.drawable ?: return null
        val values = FloatArray(9)
        screenView.imageMatrix.getValues(values)
        val sx = values[Matrix.MSCALE_X]
        val sy = values[Matrix.MSCALE_Y]
        val tx = values[Matrix.MTRANS_X]
        val ty = values[Matrix.MTRANS_Y]
        if (sx == 0f || sy == 0f) return null
        val ix = (viewX - tx) / sx
        val iy = (viewY - ty) / sy
        if (ix < 0 || iy < 0 || ix > d.intrinsicWidth || iy > d.intrinsicHeight) return null
        return Pair(ix / d.intrinsicWidth.toFloat(), iy / d.intrinsicHeight.toFloat())
    }

    private fun onOpenPressed() {
        Log.i(TAG, "OPEN_PRESSED status=${appStatus.first}")
        if (!isYouTubeInstalled()) {
            Toast.makeText(this, "Официальный YouTube не установлен", Toast.LENGTH_LONG).show()
            Log.e(TAG, "YOUTUBE_PACKAGE_MISSING $YOUTUBE_PACKAGE")
            return
        }
        if (appStatus.first == AppStatus.Running) {
            launchYouTubeNow()
            verifyInBackground()
            return
        }

        attempts = 0
        pendingLaunch = true
        probeGeneration++
        val prep = VpnService.prepare(this)
        if (prep != null) {
            Log.i(TAG, "VPN_PERMISSION_REQUEST")
            vpnPermission.launch(prep)
        } else {
            startVpnForYouTube()
        }
    }

    private fun startVpnForYouTube() {
        val p = getPreferences()
        p.edit { putString("byedpi_cmd_args", strategies[strategyIndex]) }
        Log.i(TAG, "VPN_START strategy=${strategyIndex + 1}/${strategies.size} args=${strategies[strategyIndex]}")
        ServiceManager.start(this, Mode.VPN)
        lifecycleScope.launch {
            delay(1800)
            if (pendingLaunch) {
                Log.w(TAG, "VPN_START_BROADCAST_TIMEOUT launching YouTube failsafe")
                pendingLaunch = false
                launchYouTubeNow()
            }
        }
    }

    private fun launchYouTubeNow() {
        try {
            val launch = packageManager.getLaunchIntentForPackage(YOUTUBE_PACKAGE)
            if (launch == null) {
                Log.e(TAG, "YOUTUBE_LAUNCH_INTENT_NULL")
                Toast.makeText(this, "Не удалось найти запуск YouTube", Toast.LENGTH_LONG).show()
                return
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            Log.i(TAG, "YOUTUBE_LAUNCH startActivity package=$YOUTUBE_PACKAGE")
            startActivity(launch)
        } catch (t: Throwable) {
            Log.e(TAG, "YOUTUBE_LAUNCH_FAIL", t)
            Toast.makeText(this, "Ошибка запуска YouTube: ${t.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }

    private fun prepareStrategies() {
        val gv = File(filesDir, "freevideo_googlevideo_hosts.txt").apply { writeText("googlevideo.com\n") }
        val all = File(filesDir, "freevideo_youtube_hosts.txt").apply {
            writeText(
                "googlevideo.com\nyoutube.com\nyoutu.be\nyoutubei.googleapis.com\ni.ytimg.com\nyt3.ggpht.com\nyt4.ggpht.com\ngoogleapis.com\nggpht.com\ngoogleusercontent.com\n"
            )
        }
        strategies = STRATEGY_TEMPLATES.map { it.replace("{GV}", gv.absolutePath).replace("{ALL}", all.absolutePath) }
    }

    private fun configureDefaults() {
        val p = getPreferences()
        val remembered = p.getString("fv_last_strategy_args", null)
        strategyIndex = strategies.indexOf(remembered).takeIf { it >= 0 } ?: 0
        p.edit {
            putString("byedpi_mode", "vpn")
            putString("dns_ip", "9.9.9.9")
            putBoolean("ipv6_enable", false)
            putBoolean("byedpi_enable_cmd_settings", true)
            putString("applist_type", "whitelist")
            putStringSet("selected_apps", setOf(YOUTUBE_PACKAGE))
            putString("byedpi_cmd_args", strategies[strategyIndex])
        }
    }

    private fun verifyInBackground() {
        if (appStatus.first != AppStatus.Running || strategies.isEmpty()) return
        val generation = ++probeGeneration
        val current = strategyIndex
        lifecycleScope.launch(Dispatchers.IO) {
            val ok = probeVideoRoute()
            withContext(Dispatchers.Main) {
                if (generation != probeGeneration) return@withContext
                if (ok) {
                    getPreferences().edit { putString("fv_last_strategy_args", strategies[current]) }
                    attempts = 0
                    Log.i(TAG, "ROUTE_OK strategy=${current + 1}/${strategies.size}")
                } else {
                    attempts++
                    Log.w(TAG, "ROUTE_FAIL strategy=${current + 1}/${strategies.size} attempt=$attempts")
                    if (attempts < strategies.size && appStatus.first == AppStatus.Running) {
                        strategyIndex = (strategyIndex + 1) % strategies.size
                        getPreferences().edit { putString("byedpi_cmd_args", strategies[strategyIndex]) }
                        Log.i(TAG, "ROUTE_SWITCH next=${strategyIndex + 1}/${strategies.size}")
                        ServiceManager.restart(this@MainActivity, Mode.VPN)
                    }
                }
            }
        }
    }

    private fun probeVideoRoute(): Boolean {
        val (_, portText) = getPreferences().getProxyIpAndPort()
        val port = portText.toIntOrNull() ?: 1080
        val socks = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port))
        val targets = listOf(
            "https://www.youtube.com/generate_204",
            "https://redirector.googlevideo.com/report_mapping"
        )
        for (target in targets) {
            var c: HttpsURLConnection? = null
            try {
                c = URL(target).openConnection(socks) as HttpsURLConnection
                c.connectTimeout = 7000
                c.readTimeout = 7000
                c.instanceFollowRedirects = true
                c.requestMethod = "GET"
                c.setRequestProperty("User-Agent", "Mozilla/5.0 Android FreeVideo/$VERSION")
                val code = c.responseCode
                Log.i(TAG, "PROBE target=$target code=$code")
                if (code !in 200..499) return false
            } catch (t: Throwable) {
                Log.w(TAG, "PROBE_FAIL target=$target ${t.javaClass.simpleName}: ${t.message}")
                return false
            } finally {
                c?.disconnect()
            }
        }
        return true
    }

    private fun isYouTubeInstalled(): Boolean = try {
        packageManager.getPackageInfo(YOUTUBE_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun exportLog() {
        saveLog.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "freevideo_v0.2.1.log.txt")
        })
    }

    private fun collectLogs(): String = try {
        Runtime.getRuntime().exec("logcat *:D -d").inputStream.bufferedReader().use { it.readText() }
    } catch (t: Throwable) {
        "Failed to collect logcat: ${t.javaClass.name}: ${t.message}"
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerStatusReceiver() {
        val f = IntentFilter().apply {
            addAction(STARTED_BROADCAST)
            addAction(STOPPED_BROADCAST)
            addAction(FAILED_BROADCAST)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) registerReceiver(receiver, f, RECEIVER_EXPORTED)
        else registerReceiver(receiver, f)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 41)
        }
    }
}
