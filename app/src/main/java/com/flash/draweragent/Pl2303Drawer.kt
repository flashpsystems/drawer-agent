package com.flash.draweragent

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbManager
import android.os.Build

/**
 * Opens a Prolific PL2303 USB cash drawer — a direct port of cashdraw.py /
 * cashdraw_agent.py (same VID/PID, same PL2303 init sequence, same ESC/POS
 * kick commands), using the Android USB Host API instead of pyusb/libusb.
 */
class Pl2303Drawer(private val ctx: Context) {

    companion object {
        const val VID = 0x067B   // Prolific
        const val PID = 0x2303   // PL2303
        const val ACTION_USB_PERMISSION = "com.flash.draweragent.USB_PERMISSION"

        // ESC/POS drawer-kick commands (identical to the Python agent)
        val COMMANDS: List<ByteArray> = listOf(
            byteArrayOf(0x1B, 0x70, 0x00, 0x19, 0xFA.toByte()), // ESC p 0 25 250
            byteArrayOf(0x1B, 0x70, 0x01, 0x19, 0xFA.toByte()), // ESC p 1 25 250 (alt pin)
            byteArrayOf(0x1B, 0x70, 0x00, 0x0A, 0x80.toByte())  // ESC p 0 10 128 (short pulse)
        )
    }

    private val usb: UsbManager
        get() = ctx.getSystemService(Context.USB_SERVICE) as UsbManager

    fun findDevice(): UsbDevice? =
        usb.deviceList.values.firstOrNull { it.vendorId == VID && it.productId == PID }

    /** Is the drawer physically visible on USB? (used by /health) */
    fun present(): Boolean = findDevice() != null

    fun hasPermission(): Boolean {
        val dev = findDevice() ?: return false
        return usb.hasPermission(dev)
    }

    /** Ask Android for permission to talk to the device (async; user taps OK). */
    fun requestPermission() {
        val dev = findDevice() ?: return
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_MUTABLE else 0
        val pi = PendingIntent.getBroadcast(
            ctx, 0, Intent(ACTION_USB_PERMISSION).setPackage(ctx.packageName), flags
        )
        usb.requestPermission(dev, pi)
    }

    /**
     * Pop the drawer. Returns (ok, message) — same shape the Python agent used.
     */
    @Synchronized
    fun open(): Pair<Boolean, String> {
        val dev = findDevice()
            ?: return false to "PL2303 not found — check the USB cable / OTG adapter."
        if (!usb.hasPermission(dev)) {
            requestPermission()
            return false to "USB permission not granted yet — approve the prompt on the device."
        }
        val conn = usb.openDevice(dev)
            ?: return false to "openDevice failed (device busy or no permission)."

        try {
            val intf = dev.getInterface(0)
            if (!conn.claimInterface(intf, true)) {
                return false to "claimInterface failed."
            }

            var epOut: UsbEndpoint? = null
            for (i in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(i)
                if (ep.direction == UsbConstants.USB_DIR_OUT &&
                    ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK
                ) {
                    epOut = ep; break
                }
            }
            if (epOut == null) return false to "No bulk-OUT endpoint found."

            pl2303Init(conn)
            setLineCoding(conn)
            setLines(conn, dtr = false, rts = false); Thread.sleep(30)
            setLines(conn, dtr = true, rts = true); Thread.sleep(10)

            var ok = false
            var lastErr = ""
            for (cmd in COMMANDS) {
                val n = conn.bulkTransfer(epOut, cmd, cmd.size, 1000)
                if (n >= 0) {
                    ok = true
                    Thread.sleep(80)
                } else {
                    lastErr = "bulkTransfer returned $n"
                }
            }
            Thread.sleep(60)
            setLines(conn, dtr = false, rts = false)

            return if (ok) true to "Drawer opened."
            else false to "Write failed: $lastErr"
        } catch (e: Exception) {
            return false to "Error: ${e.message}"
        } finally {
            conn.close()
        }
    }

    // ── PL2303 control transfers (map 1:1 to the pyusb ctrl_transfer calls) ──

    private fun pl2303Init(c: UsbDeviceConnection) {
        val buf = ByteArray(1)
        fun rd(v: Int) = c.controlTransfer(0xC0, 0x01, v, 0, buf, 1, 1000)
        fun wr(v: Int, i: Int) = c.controlTransfer(0x40, 0x01, v, i, null, 0, 1000)
        rd(0x8484); wr(0x0404, 0); rd(0x8484); rd(0x8383); rd(0x8484)
        wr(0x0404, 1); rd(0x8484); rd(0x8383)
        wr(0x0000, 1); wr(0x0001, 0); wr(0x0002, 0x44)
    }

    private fun setLineCoding(c: UsbDeviceConnection) {
        // 9600-8-N-1  (baud 0x2580 LE, stop 0, parity 0, data 8)
        val data = byteArrayOf(0x80.toByte(), 0x25, 0x00, 0x00, 0x00, 0x00, 0x08)
        c.controlTransfer(0x21, 0x20, 0, 0, data, data.size, 1000)
    }

    private fun setLines(c: UsbDeviceConnection, dtr: Boolean, rts: Boolean) {
        val w = (if (dtr) 0x01 else 0) or (if (rts) 0x02 else 0)
        c.controlTransfer(0x21, 0x22, w, 0, null, 0, 1000)
    }
}
