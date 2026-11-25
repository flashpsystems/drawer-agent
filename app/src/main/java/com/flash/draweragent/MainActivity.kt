package com.flash.draweragent

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var drawer: Pl2303Drawer

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { refresh() }
    }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        drawer = Pl2303Drawer(this)

        // Keep the server running as a foreground service.
        val svc = Intent(this, DrawerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc) else startService(svc)

        requestRuntimeBits()

        findViewById<Button>(R.id.btnTest).setOnClickListener {
            thread {
                val (ok, msg) = drawer.open()
                runOnUiThread {
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    refresh()
                }
            }
        }
        findViewById<Button>(R.id.btnPermission).setOnClickListener { drawer.requestPermission() }
    }

    override fun onResume() {
        super.onResume()
        val f = IntentFilter(Pl2303Drawer.ACTION_USB_PERMISSION).apply {
            addAction("android.hardware.usb.action.USB_DEVICE_ATTACHED")
            addAction("android.hardware.usb.action.USB_DEVICE_DETACHED")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            registerReceiver(usbReceiver, f, Context.RECEIVER_NOT_EXPORTED)
        else
            @Suppress("UnspecifiedRegisterReceiverFlag") registerReceiver(usbReceiver, f)
        refresh()
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(usbReceiver) } catch (_: Exception) {}
    }

    @SuppressLint("SetTextI18n")
    private fun refresh() {
        val url = "http://${DrawerService.localIp()}:${DrawerService.PORT}"
        findViewById<TextView>(R.id.txtUrl).text = "URL:  $url"
        val present = drawer.present()
        val perm = drawer.hasPermission()
        findViewById<TextView>(R.id.txtDrawer).text =
            "Drawer (PL2303):  " + if (!present) "NOT DETECTED" else if (perm) "ready ✓" else "detected — needs USB permission"
        findViewById<TextView>(R.id.txtServer).text = "HTTP server:  running on port ${DrawerService.PORT}"
    }

    private fun requestRuntimeBits() {
        // Notifications (Android 13+) so the foreground notification shows.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        // Ask to be exempt from battery optimization so the service is never killed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                    startActivity(Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName")
                    ))
                }
            } catch (_: Exception) {}
        }
        // Grab USB permission up-front if the drawer is already plugged in.
        if (drawer.present() && !drawer.hasPermission()) drawer.requestPermission()
    }
}
