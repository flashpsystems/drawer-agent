package com.flash.draweragent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Foreground service that keeps the HTTP drawer server alive for good — even
 * when the screen is off — so the POS appliance can always pop the drawer.
 */
class DrawerService : Service() {

    companion object {
        const val PORT = 9101
        const val CHANNEL = "drawer_agent"
        const val NOTIF_ID = 1
        private const val TAG = "DrawerAgent"

        /** Best LAN IPv4 of this device, for display/logging. */
        fun localIp(): String {
            try {
                for (ni in NetworkInterface.getNetworkInterfaces()) {
                    if (!ni.isUp || ni.isLoopback) continue
                    for (addr in ni.inetAddresses) {
                        if (addr is Inet4Address && !addr.isLoopbackAddress) {
                            return addr.hostAddress ?: continue
                        }
                    }
                }
            } catch (_: Exception) {}
            return "0.0.0.0"
        }
    }

    private var server: HttpServer? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification("Starting…"))
        try {
            val drawer = Pl2303Drawer(this)
            server = HttpServer(PORT, drawer) { line -> Log.i(TAG, line) }.also {
                it.start(NanoTimeoutSafe, false)
            }
            val url = "http://${localIp()}:$PORT"
            Log.i(TAG, "Drawer agent listening on $url")
            updateNotification("Listening on $url")
        } catch (e: Exception) {
            Log.e(TAG, "failed to start server: ${e.message}", e)
            updateNotification("ERROR: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        try { server?.stop() } catch (_: Exception) {}
        server = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(text: String): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Drawer Agent", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this)
        return b.setContentTitle("Flash Drawer Agent")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_drawer)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }
}

// NanoHTTPD socket read timeout (ms). Long enough to be reliable on a LAN.
private const val NanoTimeoutSafe = 10000
