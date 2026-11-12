package com.smartnet.vpn

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var pendingConfigJson: String = ""

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startSmartVpn(pendingConfigJson)
        } else {
            Toast.makeText(this, "مجوز اتصال VPN صادر نشد", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        setContentView(webView)

        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true

        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()
        webView.addJavascriptInterface(AndroidBridge(), "AndroidBridge")

        webView.loadUrl("file:///android_asset/index.html")
    }

    private fun startSmartVpn(configJson: String) {
        val intent = Intent(this, SmartVpnService::class.java).apply {
            action = SmartVpnService.ACTION_CONNECT
            putExtra("CONFIG_JSON", configJson)
        }
        startService(intent)
        Toast.makeText(this, "تونل هوشمند SmartNet فعال شد", Toast.LENGTH_SHORT).show()
    }

    private fun stopSmartVpn() {
        val intent = Intent(this, SmartVpnService::class.java).apply {
            action = SmartVpnService.ACTION_DISCONNECT
        }
        startService(intent)
        Toast.makeText(this, "اتصال VPN قطع شد", Toast.LENGTH_SHORT).show()
    }

    inner class AndroidBridge {
        @JavascriptInterface
        fun connectVpn(configJson: String) {
            runOnUiThread {
                pendingConfigJson = configJson
                val intent = VpnService.prepare(this@MainActivity)
                if (intent != null) {
                    vpnPermissionLauncher.launch(intent)
                } else {
                    startSmartVpn(configJson)
                }
            }
        }

        @JavascriptInterface
        fun disconnectVpn() {
            runOnUiThread {
                stopSmartVpn()
            }
        }

        @JavascriptInterface
        fun copyToClipboard(text: String) {
            runOnUiThread {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("SmartNetConfig", text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this@MainActivity, "کانفیگ بهینه در کلیپ‌بورد ذخیره شد", Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun detectNetworkType(): String {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return "wifi"
            val caps = cm.getNetworkCapabilities(network) ?: return "wifi"
            return when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    val tm = getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
                    val opName = tm.networkOperatorName.lowercase()
                    if (opName.contains("irancell") || opName.contains("mtn")) "irancell" else "mci"
                }
                else -> "wifi"
            }
        }
    }
}
