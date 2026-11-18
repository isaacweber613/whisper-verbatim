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

    /** Pref: the person agreed to [DISCLOSURE]. */
    const val CONSENT = "yiddish_training_consent"
    const val DISCLOSURE = "Yiddish mode sends your recording through Cloudflare to a speech model on the developer's PC. " +
        "The PC keeps the recording and the text it typed to improve the Yiddish model, " +
        "with a random install ID and the app version. There is no automatic deletion. " +
        "If the PC is unavailable, your recording goes to OpenRouter and its speech provider instead, " +
        "through Word for Word's server when using free minutes. " +
        "A quick tap uses the normal cloud model without Yiddish training."

    /**
     * Google Play requires an in-app disclosure and an Agree tap before data is kept in a way
     * people don't expect, so the Play build asks once before the first Yiddish recording.
     * Isaac (2026-11-11): only what Play requires, and only in the Play build. The GitHub builds
     * don't ask, and there is no cloud-dictation question or settings row in any build.
     */
    fun needsConsent(p: SharedPreferences) = BuildConfig.FLAVOR == "play" && !p.getBoolean(CONSENT, false)

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
     * Every Yiddish take is kept on the PC for training. [source] is saved with the take: "app" for
     * dictation, "app-share" for an audio file shared to the app.
     */
    fun transcribe(wav: ByteArray, appVersion: String, install: String, source: String = "app",
                   callback: (TranscriberClient.Result) -> Unit) {
        val request = Request.Builder()
            .url("$BASE/api/transcribe")
            .header("x-password", PASSWORD)
            .header("x-client", source)
            .header("x-app-version", appVersion)
            .header("x-install", install)
            .post(wav.toRequestBody("audio/wav".toMediaType()))
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = callback(offline(e))

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                val json = try { JSONObject(body) } catch (_: Exception) { null }
                if (response.isSuccessful && json != null) {
                    callback(TranscriberClient.Result(json.optString("text", ""), null))
                } else {
                    callback(failure(response.code, json))
                }
            }
        })
    }

    /**
     * A shared file (Isaac, 2026-11-17): a 62-second video took the PC 150 s, and Cloudflare drops
     * a request that gets no answer for 125 s, so the take was typed out but the text never came
     * back. Like the try page, a shared file starts a job on the PC (POST /api/job) and asks for it
     * every 3 s (POST /api/job/status), or 5 s after a failed ask. Blocks; call it off the main
     * thread. [progress] gets how much of the file is typed out, 0 to 1, when the PC says.
     */
    fun transcribeJob(wav: ByteArray, appVersion: String, install: String, source: String,
                      progress: (Double) -> Unit): TranscriberClient.Result {
        val start = Request.Builder()
            .url("$BASE/api/job")
            .header("x-password", PASSWORD)
            .header("x-client", source)
            .header("x-app-version", appVersion)
            .header("x-install", install)
            .post(wav.toRequestBody("audio/wav".toMediaType()))
            .build()
        val job = try {
            client.newCall(start).execute().use { response ->
                val json = try { JSONObject(response.body?.string() ?: "") } catch (_: Exception) { null }
                json?.optString("job")?.takeIf { response.isSuccessful && it.isNotBlank() }
                    ?: return failure(response.code, json)
            }
        } catch (e: IOException) {
            return offline(e)
        }
        var wait = 3000L
        while (true) {
            Thread.sleep(wait)
            val ask = Request.Builder()
                .url("$BASE/api/job/status")
                .header("x-password", PASSWORD)
                .post(JSONObject().put("job", job).toString().toRequestBody("application/json".toMediaType()))
                .build()
            val json = try {
                client.newCall(ask).execute().use { response ->
                    val json = try { JSONObject(response.body?.string() ?: "") } catch (_: Exception) { null }
                    if (!response.isSuccessful || json == null) return failure(response.code, json)
                    json
                }
            } catch (_: IOException) {
                wait = 5000L
                continue
            }
            wait = 3000L
            if (!json.optBoolean("done")) {
                if (json.has("progress")) progress(json.optDouble("progress"))
                continue
            }
            if (json.optString("error").isNotBlank()) return failure(500, json)
            return TranscriberClient.Result(json.optString("text", ""), null)
        }
    }

    private fun offline(e: IOException) = TranscriberClient.Result(null,
        if (e is java.net.UnknownHostException) "No internet connection. Your words weren't sent."
        else "Couldn't reach the Yiddish model. Try again.")

    private fun failure(code: Int, json: JSONObject?): TranscriberClient.Result {
        val msg = when (code) {
            503 -> "The Yiddish model is offline right now (the computer running it is off)."
            else -> "The Yiddish model had a problem (${json?.optString("error")?.takeIf { it.isNotBlank() } ?: code}). Try again."
        }
        return TranscriberClient.Result(null, msg, code)
    }

}
