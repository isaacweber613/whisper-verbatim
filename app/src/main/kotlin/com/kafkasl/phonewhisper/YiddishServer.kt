package com.kafkasl.phonewhisper

import android.content.SharedPreferences
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Yiddish dictation (Isaac, 2026-11-08): with Yiddish picked as the language, holding the mic
 * button, or pressing the second (Yiddish) shortcut, sends that one recording to the Yiddish model
 * (ivrit.ai yi-whisper-large-v3) running on Isaac's PC, reached through the `yiddishtry` Cloudflare
 * Worker (yiddish-stt repo, tryout/). A normal tap keeps using the cloud model with Auto language.
 * Nothing changes unless Yiddish is the picked language.
 */
object YiddishServer {
    private const val URL = "https://yiddishtry.ynbsd6242.workers.dev/api/transcribe"
    // The Worker's password for its try-it page; it only gates the PC model, which holds no data.
    private const val PASSWORD = "4837"

    fun enabled(p: SharedPreferences) = p.getString("language", "auto") == "yi"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun transcribe(wav: ByteArray, callback: (TranscriberClient.Result) -> Unit) {
        val request = Request.Builder()
            .url(URL)
            .header("x-password", PASSWORD)
            .post(wav.toRequestBody("audio/wav".toMediaType()))
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = callback(TranscriberClient.Result(null,
                if (e is java.net.UnknownHostException) "No internet connection. Your words weren't sent."
                else "Couldn't reach the Yiddish model. Try again."))

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                val json = try { JSONObject(body) } catch (_: Exception) { null }
                if (response.isSuccessful && json != null) {
                    callback(TranscriberClient.Result(json.optString("text", ""), null))
                } else {
                    val msg = when (response.code) {
                        503 -> "The Yiddish model is offline right now (the computer running it is off)."
                        else -> "The Yiddish model had a problem (${json?.optString("error")?.takeIf { it.isNotBlank() } ?: response.code}). Try again."
                    }
                    callback(TranscriberClient.Result(null, msg, response.code))
                }
            }
        })
    }
}
