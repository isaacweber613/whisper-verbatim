package com.kafkasl.phonewhisper

import android.util.Base64
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Sends recorded audio to OpenRouter's speech-to-text endpoint and returns the
 * transcript exactly as the model wrote it. No cleanup or rewording happens here.
 */
object TranscriberClient {
    data class Result(val text: String?, val error: String?)
    data class CloudModel(
        val id: String,
        val name: String,
        val company: String,
        val price: String,
        val details: String
    ) {
        val title get() = "$name ($company)"
    }

    const val DEFAULT_MODEL = "microsoft/mai-transcribe-2"

    val CLOUD_MODELS = listOf(
        CloudModel("microsoft/mai-transcribe-2", "MAI-Transcribe 2", "Microsoft", "\$0.10/hour",
            "Most accurate of the three tested (2.0% word errors on the Artificial Analysis benchmark) and the fastest, " +
            "about half a second per clip. Has its own verbatim switch, which Verbatim mode turns on.\n\n" +
            "Still a preview. The \$0.10/hour price is a launch promotion through December 31, 2026."),
        CloudModel("openai/gpt-transcribe", "GPT Transcribe", "OpenAI", "\$0.27/hour",
            "OpenAI's speech-to-text model, released August 2026. Has no verbatim switch, " +
            "so it decides on its own whether to keep \"um\" and \"uh.\""),
        CloudModel("mistralai/voxtral-mini-transcribe-3-0", "Voxtral Mini Transcribe 3", "Mistral", "\$0.21/hour",
            "Close second on accuracy (2.2% word errors) and about as fast, around half a second to 1 second per clip. " +
            "Kept every \"um\" and repeated word in testing. Released October 2026."),
        CloudModel("elevenlabs/scribe-v2", "Scribe v2", "ElevenLabs", "\$0.22/hour",
            "Close second on accuracy (2.2% word errors) but slower, about 1.5 seconds per clip. " +
            "Verbatim by default. Writes times with :00, so \"six\" becomes \"6:00.\"")
    )

    /** Spoken-language choices besides "auto", where the model guesses. */
    val LANGUAGES = linkedMapOf("he" to "Hebrew", "yi" to "Yiddish")

    /**
     * The language code each model is sent for each choice; a missing entry means the
     * model guesses. Tested with real Yiddish through OpenRouter on 2026-11-05:
     * GPT Transcribe rejects every Yiddish code ("yi", "yid") with error 400, and with
     * Hebrew set it wrote Yiddish better than guessing, so Yiddish sends "he" to it.
     * MAI-Transcribe 2 lists Hebrew but not Yiddish; Voxtral lists neither.
     */
    private val LANGUAGE_CODES = mapOf(
        "elevenlabs/scribe-v2" to mapOf("he" to "he", "yi" to "yi"),
        "openai/gpt-transcribe" to mapOf("he" to "he", "yi" to "he"),
        "microsoft/mai-transcribe-2" to mapOf("he" to "he"),
    )

    fun languagesFor(model: String): List<String> = LANGUAGE_CODES[model]?.keys?.toList() ?: emptyList()

    fun languageCode(model: String, language: String): String? = LANGUAGE_CODES[model]?.get(language)

    /** Shown in the app for a model ID: the friendly name for a built-in model, the raw ID for a custom one. */
    fun titleFor(id: String) = CLOUD_MODELS.firstOrNull { it.id == id }?.title ?: id

    fun summaryFor(id: String) = CLOUD_MODELS.firstOrNull { it.id == id }
        ?.let { "${it.title} · ${it.price}" } ?: "Custom: $id"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(70, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun parseResponse(json: String): Result = try {
        val obj = JSONObject(json)
        when {
            obj.has("text") -> Result(obj.getString("text"), null)
            obj.has("error") -> Result(null, obj.getJSONObject("error").optString("message", "Unknown error"))
            else -> Result(null, "Unknown response")
        }
    } catch (e: Exception) {
        Result(null, e.message ?: "Parse error")
    }

    /**
     * Provider-specific settings that make each provider keep fillers ("um", "uh"),
     * repeats, and false starts. OpenRouter forwards only the block for the provider
     * that serves the request, so listing several is safe.
     */
    private fun verbatimOptions(): JSONObject = JSONObject()
        // Microsoft (MAI-Transcribe): verbatim vs clean style switch
        .put("azure", JSONObject().put("enhancedMode",
            JSONObject().put("modelOptions", JSONObject().put("transcribeStyle", "verbatim"))))
        // ElevenLabs (Scribe): verbatim is its default; make it explicit
        .put("elevenlabs", JSONObject().put("no_verbatim", false))
        // Deepgram (Nova): strips "uh" and "um" unless asked to keep them
        .put("deepgram", JSONObject().put("filler_words", true))
        // AssemblyAI (Universal): removes fillers unless disfluencies is on
        .put("assemblyai", JSONObject().put("disfluencies", true))

    /** With verbatim off, no provider options are sent and each model uses its own default. */
    fun buildBody(wavData: ByteArray, model: String, verbatim: Boolean, language: String = "auto"): String {
        val b64 = Base64.encodeToString(wavData, Base64.NO_WRAP)
        val body = JSONObject()
            .put("model", model)
            .put("input_audio", JSONObject().put("data", b64).put("format", "wav"))
        languageCode(model, language)?.let { body.put("language", it) }
        if (verbatim) body.put("provider", JSONObject().put("options", verbatimOptions()))
        return body.toString()
    }

    fun transcribe(wavData: ByteArray, apiKey: String, model: String, verbatim: Boolean, language: String, callback: (Result) -> Unit) {
        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/audio/transcriptions")
            .header("Authorization", "Bearer $apiKey")
            .header("X-Title", "Word for Word")
            .post(buildBody(wavData, model, verbatim, language).toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = callback(Result(null, networkError(e)))
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    callback(parseResponse(body))
                } else {
                    callback(Result(null, httpError(response.code, parseResponse(body).error, model)))
                }
            }
        })
    }

    /** Plain-language message for a failed connection. */
    fun networkError(e: IOException): String = when (e) {
        is java.net.UnknownHostException -> "No internet connection. Your words weren't sent."
        is java.net.SocketTimeoutException -> "OpenRouter took too long to answer. Try again."
        else -> "Couldn't reach OpenRouter (${e.message ?: "connection error"}). Try again."
    }

    /** Plain-language message for an error answer from OpenRouter, by HTTP status code. */
    fun httpError(code: Int, detail: String?, model: String): String = when (code) {
        401 -> "OpenRouter rejected your API key. Open Word for Word and check the key."
        402 -> "Your OpenRouter account is out of credit. Add credit at openrouter.ai."
        403 -> "OpenRouter refused the request: ${detail ?: "forbidden"}."
        404 -> "OpenRouter doesn't have the model \"$model\". Pick another in Word for Word."
        408, 504 -> "The transcription service timed out. Try again."
        413 -> "That recording is too long to send in one piece. Try a shorter one."
        429 -> "Too many requests right now. Wait a moment and try again."
        in 500..599 -> "The transcription service is having problems (error $code). Try again, or pick another model."
        else -> "Transcription failed (error $code): ${detail ?: "no details"}"
    }

    /** Checks a key with OpenRouter. Calls back with null when the key works, or a message when it doesn't. */
    fun checkKey(apiKey: String, callback: (String?) -> Unit) {
        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/key")
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = callback(networkError(e))
            override fun onResponse(call: Call, response: Response) {
                response.close()
                callback(when {
                    response.isSuccessful -> null
                    response.code == 401 -> "OpenRouter doesn't recognize this key. Check that you pasted all of it."
                    else -> "Couldn't check the key (error ${response.code})."
                })
            }
        })
    }
}
