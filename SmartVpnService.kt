package com.smartnet.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log

class SmartVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "com.smartnet.vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.smartnet.vpn.DISCONNECT"
    }

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val config = intent.getStringExtra("CONFIG_JSON") ?: ""
                establishTunnel(config)
            }
            ACTION_DISCONNECT -> {
                closeTunnel()
            }
        }
        return START_STICKY
    }

    private fun establishTunnel(configJson: String) {
        if (vpnInterface != null) return
        try {
            val builder = Builder()
                .setSession("SmartNet VPN")
                .addAddress("10.0.0.2", 32)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
                .addRoute("0.0.0.0", 0)
                .setMtu(1500)

            // Bypass self to prevent routing loop
            builder.addDisallowedApplication(packageName)

            vpnInterface = builder.establish()
            Log.i("SmartNetVPN", "Tunnel established with config: $configJson")
            // Note: Attach libv2ray / libbox tun2socks engine fd here: vpnInterface?.fd
        } catch (e: Exception) {
            Log.e("SmartNetVPN", "Failed to establish VPN tunnel", e)
        }
    }

    private fun closeTunnel() {
        try {
            vpnInterface?.close()
            vpnInterface = null
            stopSelf()
        } catch (e: Exception) {
            Log.e("SmartNetVPN", "Error closing tunnel", e)
        }
    }

    override fun onDestroy() {
        closeTunnel()
        super.onDestroy()
    }
}
