package io.github.romanvht.byedpi.activities

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
import androidx.core.content.edit
import io.github.romanvht.byedpi.data.FAILED_BROADCAST
import io.github.romanvht.byedpi.data.Mode
import io.github.romanvht.byedpi.data.STARTED_BROADCAST
import io.github.romanvht.byedpi.data.STOPPED_BROADCAST
import io.github.romanvht.byedpi.services.ServiceManager
import io.github.romanvht.byedpi.utility.getPreferences
import java.io.File

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "FreeVideo"
        private const val VERSION = "0.2.2"
        private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        private const val SCREEN_INTRO = 2
        private const val SCREEN_MAIN = 3
        private const val SCREEN_PAY = 4
        private const val SCREEN_SETTINGS = 5
        private const val SCREEN_OTHER = 6

        private val STRATEGY_TEMPLATES = listOf(
            "-I 0.0.0.0 -U -H {GV} -o1 -An",
            "-I 0.0.0.0 -U -H {GV} -o1 -r-5+se -An",
            "-I 0.0.0.0 -U -H {ALL} -o1 -r-5+se -An",
            "-I 0.0.0.0 -U -H {GV} -s1+s -d3+s -An"
        )
    }

    private lateinit var root: FrameLayout
    private lateinit var screenView: ImageView
    private var screen = SCREEN_INTRO
    private var networkPrepared = false
    private var receiverRegistered = false
    private var strategyIndex = 0
    private var strategies: List<String> = emptyList()

    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        Log.i(TAG, "VPN_PERMISSION_RESULT code=${result.resultCode}")
        if (result.resultCode == RESULT_OK) startNetworkSafe()
        launchYouTubeSafe()
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
                when (intent?.action) {
                    STARTED_BROADCAST -> Log.i(TAG, "VPN_STARTED")
                    STOPPED_BROADCAST -> Log.i(TAG, "VPN_STOPPED")
                    FAILED_BROADCAST -> Log.e(TAG, "VPN_FAILED")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "RECEIVER_FAIL", t)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            buildImageUi()
            showScreen(SCREEN_INTRO)
            tryHideSystemUi()
            Log.i(TAG, "APP_START_OK version=$VERSION safeStart=true")
        } catch (t: Throwable) {
            Log.e(TAG, "APP_START_FATAL", t)
            val fallback = FrameLayout(this).apply { setBackgroundColor(android.graphics.Color.BLACK) }
            setContentView(fallback)
            Toast.makeText(this, "FreeVideo: ошибка экрана ${t.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        tryHideSystemUi()
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            try { unregisterReceiver(receiver) } catch (_: Throwable) {}
            receiverRegistered = false
        }
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when (screen) {
            SCREEN_SETTINGS, SCREEN_OTHER, SCREEN_PAY -> showScreen(SCREEN_MAIN)
            else -> super.onBackPressed()
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

    private fun tryHideSystemUi() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false)
                window.insetsController?.let {
                    it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                run {
                    window.decorView.systemUiVisibility =
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "FULLSCREEN_FAIL ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun showScreen(which: Int) {
        if (!::screenView.isInitialized) return
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
                    ?: throw IllegalStateException("Bitmap decode returned null")
                screenView.setImageBitmap(bitmap)
            }
            Log.i(TAG, "UI_SCREEN_OK screen=$which asset=$asset")
        } catch (t: Throwable) {
            Log.e(TAG, "UI_ASSET_FAIL asset=$asset", t)
            Toast.makeText(this, "FreeVideo: не удалось открыть экран", Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun handleTouch(e: MotionEvent): Boolean {
        if (e.action != MotionEvent.ACTION_UP) return true
        val p = imagePoint(e.x, e.y) ?: return true
        val x = p.first
        val y = p.second
        when (screen) {
            SCREEN_INTRO -> if (y > 0.74f) showScreen(SCREEN_MAIN)
            SCREEN_MAIN -> when {
                y > 0.72f -> onOpenPressed()
                x > 0.72f && y < 0.23f -> showScreen(SCREEN_SETTINGS)
                x < 0.28f && y < 0.23f -> showScreen(SCREEN_OTHER)
            }
            SCREEN_SETTINGS -> when {
                x < 0.22f && y < 0.16f -> showScreen(SCREEN_MAIN)
                y > 0.82f -> showScreen(SCREEN_MAIN)
            }
            SCREEN_OTHER -> if (x < 0.25f && y < 0.22f) showScreen(SCREEN_MAIN)
            SCREEN_PAY -> if (y > 0.88f) showScreen(SCREEN_MAIN)
        }
        return true
    }

    private fun imagePoint(viewX: Float, viewY: Float): Pair<Float, Float>? {
        return try {
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
            Pair(ix / d.intrinsicWidth.toFloat(), iy / d.intrinsicHeight.toFloat())
        } catch (t: Throwable) {
            Log.e(TAG, "TOUCH_MAP_FAIL", t)
            null
        }
    }

    private fun onOpenPressed() {
        Log.i(TAG, "OPEN_PRESSED")
        if (!isYouTubeInstalled()) {
            Toast.makeText(this, "Официальный YouTube не установлен", Toast.LENGTH_LONG).show()
            Log.e(TAG, "YOUTUBE_PACKAGE_MISSING")
            return
        }
        try {
            val prep = VpnService.prepare(this)
            if (prep != null) {
                Log.i(TAG, "VPN_PERMISSION_REQUEST")
                vpnPermission.launch(prep)
                return
            }
        } catch (t: Throwable) {
            Log.e(TAG, "VPN_PREPARE_FAIL", t)
        }
        startNetworkSafe()
        launchYouTubeSafe()
    }

    private fun prepareNetworkLazy() {
        if (networkPrepared) return
        try {
            val gv = File(filesDir, "freevideo_googlevideo_hosts.txt")
            gv.writeText("googlevideo.com\n")
            val all = File(filesDir, "freevideo_youtube_hosts.txt")
            all.writeText("googlevideo.com\nyoutube.com\nyoutu.be\nyoutubei.googleapis.com\ni.ytimg.com\nyt3.ggpht.com\nyt4.ggpht.com\ngoogleapis.com\nggpht.com\ngoogleusercontent.com\n")
            strategies = STRATEGY_TEMPLATES.map {
                it.replace("{GV}", gv.absolutePath).replace("{ALL}", all.absolutePath)
            }
            val p = getPreferences()
            p.edit {
                putString("byedpi_mode", "vpn")
                putString("dns_ip", "9.9.9.9")
                putBoolean("ipv6_enable", false)
                putBoolean("byedpi_enable_cmd_settings", true)
                putString("applist_type", "whitelist")
                putStringSet("selected_apps", setOf(YOUTUBE_PACKAGE))
                putString("byedpi_cmd_args", strategies.first())
            }
            registerReceiverLazy()
            networkPrepared = true
            Log.i(TAG, "NETWORK_PREPARE_OK strategies=${strategies.size}")
        } catch (t: Throwable) {
            networkPrepared = false
            Log.e(TAG, "NETWORK_PREPARE_FAIL", t)
        }
    }

    private fun registerReceiverLazy() {
        if (receiverRegistered) return
        try {
            val f = IntentFilter().apply {
                addAction(STARTED_BROADCAST)
                addAction(STOPPED_BROADCAST)
                addAction(FAILED_BROADCAST)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(receiver, f, RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(receiver, f)
            }
            receiverRegistered = true
            Log.i(TAG, "RECEIVER_REGISTER_OK")
        } catch (t: Throwable) {
            Log.e(TAG, "RECEIVER_REGISTER_FAIL", t)
        }
    }

    private fun startNetworkSafe() {
        try {
            prepareNetworkLazy()
            if (strategies.isNotEmpty()) {
                getPreferences().edit { putString("byedpi_cmd_args", strategies[strategyIndex]) }
            }
            ServiceManager.start(this, Mode.VPN)
            Log.i(TAG, "VPN_START_CALLED")
        } catch (t: Throwable) {
            Log.e(TAG, "VPN_START_THROW", t)
        }
    }

    private fun launchYouTubeSafe() {
        try {
            val launch = packageManager.getLaunchIntentForPackage(YOUTUBE_PACKAGE)
            if (launch == null) {
                Log.e(TAG, "YOUTUBE_LAUNCH_INTENT_NULL")
                Toast.makeText(this, "Не удалось найти запуск YouTube", Toast.LENGTH_LONG).show()
                return
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            startActivity(launch)
            Log.i(TAG, "YOUTUBE_START_ACTIVITY_OK")
        } catch (t: Throwable) {
            Log.e(TAG, "YOUTUBE_START_ACTIVITY_FAIL", t)
            Toast.makeText(this, "Ошибка запуска YouTube: ${t.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }

    private fun isYouTubeInstalled(): Boolean {
        return try {
            packageManager.getPackageInfo(YOUTUBE_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (t: Throwable) {
            Log.e(TAG, "YOUTUBE_PACKAGE_CHECK_FAIL", t)
            false
        }
    }
}
