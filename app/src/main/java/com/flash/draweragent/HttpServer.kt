package com.flash.draweragent

import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject

/**
 * Tiny HTTP server exposing the SAME contract as cashdraw_agent.py, so the
 * parking server needs no changes — just point drawer_agent_url at this device:
 *
 *   GET  /health  -> {"ok": true, "drawer_present": <bool>}
 *   GET  /open    -> {"ok": <bool>, "message": "..."}   (handy browser test)
 *   POST /open    -> same
 */
class HttpServer(
    port: Int,
    private val drawer: Pl2303Drawer,
    private val onLog: (String) -> Unit
) : NanoHTTPD("0.0.0.0", port) {

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri.trimEnd('/')
        return try {
            when (uri) {
                "/health" -> {
                    json(Response.Status.OK, true, mapOf("drawer_present" to drawer.present()))
                }
                "/open" -> {
                    val (ok, msg) = drawer.open()
                    onLog("${session.remoteIpAddress} /open -> ok=$ok ($msg)")
                    val status = if (ok) Response.Status.OK else Response.Status.INTERNAL_ERROR
                    jsonMsg(status, ok, msg)
                }
                else -> jsonMsg(Response.Status.NOT_FOUND, false, "not found")
            }
        } catch (e: Exception) {
            jsonMsg(Response.Status.INTERNAL_ERROR, false, "error: ${e.message}")
        }
    }

    private fun json(status: Response.Status, ok: Boolean, extra: Map<String, Any?>): Response {
        val o = JSONObject().put("ok", ok)
        for ((k, v) in extra) o.put(k, v)
        return newFixedLengthResponse(status, "application/json", o.toString())
    }

    private fun jsonMsg(status: Response.Status, ok: Boolean, message: String): Response =
        newFixedLengthResponse(
            status, "application/json",
            JSONObject().put("ok", ok).put("message", message).toString()
        )
}
