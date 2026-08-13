package io.github.romanvht.byedpi.activities

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
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
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "YouTubeAccess"
        private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        private val STRATEGIES = listOf(
            "-o1 -r-5+se -a1",
            "-o1 -a1 -At,r,s -d1 -a1",
            "-d1 -s3+s -a1",
            "-o1+s -d3+s -a1",
            "-f-1 -Qr -s1+sm -d3+s -s5+sm -o2 -a1 -As -r1+s -d8+s -a1",
            "-d1 -s1+s -r1+s -f-1 -t8 -a1"
        )
    }

    private lateinit var statusText: TextView
    private lateinit var detailsText: TextView
    private lateinit var mainButton: Button
    private lateinit var stopButton: Button
    private var strategyIndex = 0
    private var attempts = 0
    private var verifyToken = 0
    private var userRequestedStop = false

    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            setStatus("Запускаю защитный маршрут…")
            ServiceManager.start(this, Mode.VPN)
        } else setStatus("Нужно разрешить VPN для YouTube")
    }

    private val saveLog = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@registerForActivityResult
        lifecycleScope.launch(Dispatchers.IO) {
            try { contentResolver.openOutputStream(uri)?.use { it.write(collectLogs().toByteArray()) } }
            catch (e: IOException) { Log.e(TAG, "log save failed", e) }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                STARTED_BROADCAST -> { setStatus("Маршрут поднят. Проверяю YouTube…"); verifyRouteAndLaunch() }
                STOPPED_BROADCAST -> { if (userRequestedStop) setStatus("Отключено"); updateButtons() }
                FAILED_BROADCAST -> {
                    setStatus("Не удалось поднять маршрут. Сохраните лог.")
                    appendDetail("VPN FAILED sender=${intent.getIntExtra(SENDER, -1)}")
                    updateButtons()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureDefaults(); buildUi(); registerStatusReceiver(); requestNotificationPermissionIfNeeded(); updateButtons()
        appendDetail("YouTube Access v0.1.0")
        appendDetail("Target: $YOUTUBE_PACKAGE only")
        appendDetail("Engine: ByeByeDPI v1.7.7 / HEV SOCKS tunnel")
    }

    override fun onResume() { super.onResume(); updateButtons() }
    override fun onDestroy() { verifyToken++; try { unregisterReceiver(receiver) } catch (_: Exception) {}; super.onDestroy() }

    private fun configureDefaults() {
        val p = getPreferences(); val remembered = p.getString("yt_last_strategy", null)
        strategyIndex = STRATEGIES.indexOf(remembered).takeIf { it >= 0 } ?: 0
        p.edit {
            putString("byedpi_mode", "vpn"); putString("dns_ip", "1.1.1.1"); putBoolean("ipv6_enable", false)
            putBoolean("byedpi_enable_cmd_settings", true); putString("applist_type", "whitelist")
            putStringSet("selected_apps", setOf(YOUTUBE_PACKAGE)); putString("byedpi_cmd_args", STRATEGIES[strategyIndex])
        }
        Log.i(TAG, "strategy[$strategyIndex]=${STRATEGIES[strategyIndex]}")
    }

    private fun buildUi() {
        val bg=Color.rgb(5,11,23); val panel=Color.rgb(13,25,43); val muted=Color.rgb(149,165,186); val accent=Color.rgb(226,35,48)
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER_HORIZONTAL; setPadding(dp(22),dp(34),dp(22),dp(22)); setBackgroundColor(bg) }
        root.addView(TextView(this).apply { text="YOUTUBE ACCESS"; setTextColor(Color.WHITE); textSize=30f; typeface=Typeface.DEFAULT_BOLD; gravity=Gravity.CENTER },LinearLayout.LayoutParams(-1,-2))
        root.addView(TextView(this).apply { text="Автоматический доступ к YouTube"; setTextColor(muted); textSize=15f; gravity=Gravity.CENTER; setPadding(0,dp(7),0,dp(25)) },LinearLayout.LayoutParams(-1,-2))
        val card=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; setPadding(dp(18),dp(20),dp(18),dp(20)); setBackgroundColor(panel) }
        statusText=TextView(this).apply { text="Готов к запуску"; setTextColor(Color.WHITE); textSize=19f; typeface=Typeface.DEFAULT_BOLD; gravity=Gravity.CENTER }
        card.addView(statusText,LinearLayout.LayoutParams(-1,-2))
        card.addView(TextView(this).apply { text="VPN используется только для официального приложения YouTube"; setTextColor(muted); textSize=13f; gravity=Gravity.CENTER; setPadding(0,dp(10),0,0) },LinearLayout.LayoutParams(-1,-2))
        root.addView(card,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(22) })
        mainButton=Button(this).apply { text="ВКЛЮЧИТЬ YOUTUBE"; textSize=17f; setTextColor(Color.WHITE); setBackgroundColor(accent); minHeight=dp(62); setOnClickListener { onMainClick() } }
        root.addView(mainButton,LinearLayout.LayoutParams(-1,dp(62)).apply { bottomMargin=dp(10) })
        stopButton=Button(this).apply { text="ОТКЛЮЧИТЬ"; textSize=15f; setOnClickListener { userRequestedStop=true; verifyToken++; ServiceManager.stop(this@MainActivity); setStatus("Отключаю…") } }
        root.addView(stopButton,LinearLayout.LayoutParams(-1,dp(54)).apply { bottomMargin=dp(10) })
        root.addView(Button(this).apply { text="СОХРАНИТЬ ЛОГ"; textSize=14f; setOnClickListener { saveLog.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type="text/plain"; putExtra(Intent.EXTRA_TITLE,"youtube_access_v0.1.0.log") }) } },LinearLayout.LayoutParams(-1,dp(50)).apply { bottomMargin=dp(16) })
        root.addView(TextView(this).apply { text="Диагностика"; setTextColor(Color.WHITE); textSize=14f; typeface=Typeface.DEFAULT_BOLD },LinearLayout.LayoutParams(-1,-2))
        val scroll=ScrollView(this); detailsText=TextView(this).apply { setTextColor(muted); textSize=12f; typeface=Typeface.MONOSPACE; setPadding(dp(8),dp(8),dp(8),dp(8)) }; scroll.addView(detailsText)
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f)); setContentView(root)
    }

    private fun onMainClick() {
        userRequestedStop=false
        if (!isYouTubeInstalled()) { setStatus("Официальный YouTube не установлен"); appendDetail("Package not found: $YOUTUBE_PACKAGE"); return }
        if (appStatus.first==AppStatus.Running) { setStatus("Маршрут уже работает"); verifyRouteAndLaunch(); return }
        attempts=0; verifyToken++
        val prep=VpnService.prepare(this)
        if (prep!=null) vpnPermission.launch(prep) else { setStatus("Запускаю защитный маршрут…"); ServiceManager.start(this,Mode.VPN) }
    }

    private fun verifyRouteAndLaunch() {
        val token=++verifyToken; val current=STRATEGIES[strategyIndex]
        appendDetail("Probe strategy ${strategyIndex+1}/${STRATEGIES.size}: $current")
        lifecycleScope.launch(Dispatchers.IO) {
            val result=probeYouTubeViaLocalSocks()
            withContext(Dispatchers.Main) {
                if (token!=verifyToken || userRequestedStop) return@withContext
                if (result) {
                    getPreferences().edit { putString("yt_last_strategy",current) }; setStatus("YouTube готов")
                    appendDetail("OK strategy ${strategyIndex+1}"); updateButtons(); launchYouTube()
                } else {
                    attempts++
                    if (attempts>=STRATEGIES.size) { setStatus("Маршрут не найден. Сохраните лог."); appendDetail("FAIL all strategies"); updateButtons(); return@withContext }
                    strategyIndex=(strategyIndex+1)%STRATEGIES.size; val next=STRATEGIES[strategyIndex]
                    getPreferences().edit { putString("byedpi_cmd_args",next) }; setStatus("Меняю стратегию ${strategyIndex+1}/${STRATEGIES.size}…"); appendDetail("Switch -> $next")
                    ServiceManager.restart(this@MainActivity,Mode.VPN)
                }
            }
        }
    }

    private fun probeYouTubeViaLocalSocks(): Boolean {
        val (_,portText)=getPreferences().getProxyIpAndPort(); val port=portText.toIntOrNull()?:1080
        val socks=Proxy(Proxy.Type.SOCKS,InetSocketAddress("127.0.0.1",port))
        for (target in listOf("https://www.youtube.com/generate_204","https://youtubei.googleapis.com/","https://manifest.googlevideo.com/")) {
            var conn:HttpsURLConnection?=null
            try {
                conn=URL(target).openConnection(socks) as HttpsURLConnection; conn.connectTimeout=7000; conn.readTimeout=7000; conn.instanceFollowRedirects=true; conn.requestMethod="GET"
                conn.setRequestProperty("User-Agent","Mozilla/5.0 Android YouTubeAccess/0.1"); val code=conn.responseCode; Log.i(TAG,"SOCKS $target -> $code")
                if (code !in 200..499) return false
            } catch (e:Exception) { Log.w(TAG,"SOCKS failed $target: ${e.javaClass.simpleName}: ${e.message}"); return false }
            finally { conn?.disconnect() }
        }
        return true
    }

    private fun launchYouTube() { packageManager.getLaunchIntentForPackage(YOUTUBE_PACKAGE)?.also { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(it) } ?: setStatus("YouTube не установлен") }
    private fun isYouTubeInstalled():Boolean=try { packageManager.getPackageInfo(YOUTUBE_PACKAGE,0); true } catch (_:PackageManager.NameNotFoundException) { false }
    private fun updateButtons() { val running=appStatus.first==AppStatus.Running; mainButton.text=if(running)"ОТКРЫТЬ YOUTUBE" else "ВКЛЮЧИТЬ YOUTUBE"; stopButton.visibility=if(running)View.VISIBLE else View.GONE }
    private fun setStatus(text:String) { statusText.text=text; appendDetail(text) }
    private fun appendDetail(text:String) { if(!::detailsText.isInitialized)return; val old=detailsText.text?.toString().orEmpty(); val line="${System.currentTimeMillis()%1000000}: $text"; detailsText.text=if(old.isBlank())line else "$old\n$line" }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerStatusReceiver() {
        val f=IntentFilter().apply { addAction(STARTED_BROADCAST); addAction(STOPPED_BROADCAST); addAction(FAILED_BROADCAST) }
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU) registerReceiver(receiver,f,RECEIVER_EXPORTED) else registerReceiver(receiver,f)
    }
    private fun requestNotificationPermissionIfNeeded() { if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),41) }
    private fun collectLogs():String=try { Runtime.getRuntime().exec("logcat *:D -d").inputStream.bufferedReader().use { it.readText() } } catch(e:Exception) { "Failed to collect logcat: ${e.javaClass.name}: ${e.message}" }
    private fun dp(v:Int):Int=(v*resources.displayMetrics.density).toInt()
}
