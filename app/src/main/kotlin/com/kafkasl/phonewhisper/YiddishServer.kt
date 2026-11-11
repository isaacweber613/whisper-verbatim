package com.kafkasl.phonewhisper

import android.content.Context
import android.content.SharedPreferences
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Yiddish dictation (Isaac, 2026-11-08): with Yiddish picked as the language, holding the mic
 * button, or pressing the second (Yiddish) shortcut, sends that one recording to the Yiddish model
 * (ivrit.ai yi-whisper-large-v3) running on Isaac's PC, reached through the `yiddishtry` Cloudflare
 * Worker (yiddish-stt repo, tryout/). A normal tap keeps using the cloud model with Auto language.
 * Nothing changes unless Yiddish is the picked language.
 *
 * The PC keeps every take for training, in every build including Play. See PRIVACY.md.
 */
object YiddishServer {
    private const val BASE = "https://yiddishtry.ynbsd6242.workers.dev"
    // The Worker's password for its try-it page; it gates the PC model and the saved takes.
    private const val PASSWORD = "4837"

    fun enabled(p: SharedPreferences) =
        p.getString("language", "auto") == "yi"

    /**
     * A random id for this install, for voice-disjoint training splits. It is not linked to the
     * person and is not the Android device id.
     */
    fun installId(context: Context): String {
        val p = context.getSharedPreferences("phonewhisper", Context.MODE_PRIVATE)
        p.getString("yiddish_install", null)?.let { return it }
        return UUID.randomUUID().toString().also { p.edit().putString("yiddish_install", it).apply() }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Isaac (2026-11-08): when the PC with the Yiddish model is off, use the normal model (Auto). */
    const val OFF_NOTICE = "The Yiddish model is off. Used your normal model."

    /** True when the PC is off (the Worker answers 503) or the Worker can't be reached. */
    fun isOff(result: TranscriberClient.Result) =
        result.code == 503 || (result.code == null && result.error != null)

    /**
     * [callback] gets the result and the take's id on the PC (every Yiddish take is kept for training).
     * [source] is saved with the take: "app" for dictation, "app-share" for an audio file shared to the app.
     */
    fun transcribe(wav: ByteArray, appVersion: String, install: String, source: String = "app",
                   callback: (TranscriberClient.Result, String?) -> Unit) {
        val request = Request.Builder()
            .url("$BASE/api/transcribe")
            .header("x-password", PASSWORD)
            .header("x-client", source)
            .header("x-app-version", appVersion)
            .header("x-install", install)
            .post(wav.toRequestBody("audio/wav".toMediaType()))
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = callback(TranscriberClient.Result(null,
                if (e is java.net.UnknownHostException) "No internet connection. Your words weren't sent."
                else "Couldn't reach the Yiddish model. Try again."), null)

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                val json = try { JSONObject(body) } catch (_: Exception) { null }
                if (response.isSuccessful && json != null) {
                    callback(TranscriberClient.Result(json.optString("text", ""), null), json.optString("id").ifBlank { null })
                } else {
                    val msg = when (response.code) {
                        503 -> "The Yiddish model is offline right now (the computer running it is off)."
                        else -> "The Yiddish model had a problem (${json?.optString("error")?.takeIf { it.isNotBlank() } ?: response.code}). Try again."
                    }
                    callback(TranscriberClient.Result(null, msg, response.code), null)
                }
            }
        })
    }

    /**
     * Sends what happened to a take: the text the person ended up with, or just an outcome such as
     * `sent_unchanged` or `cut_down` when there is no corrected text. [appended] marks words typed
     * after the take in an empty box; training reviews those before using them.
     */
    fun sendCorrection(id: String, text: String?, outcome: String, appVersion: String, install: String,
                       appended: Boolean) {
        val body = JSONObject().put("id", id).put("outcome", outcome).put("appended", appended)
        if (!text.isNullOrBlank()) body.put("text", text)
        val request = Request.Builder()
            .url("$BASE/api/correction")
            .header("x-password", PASSWORD)
            .header("x-app-version", appVersion)
            .header("x-install", install)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) { response.close() }
        })
    }
}
