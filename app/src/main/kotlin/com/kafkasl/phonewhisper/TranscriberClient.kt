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
    /**
     * [code] and [detail] are OpenRouter's HTTP status and own error text for a failed answer;
     * null for success or no connection.
     */
    data class Result(val text: String?, val error: String?, val code: Int? = null, val detail: String? = null)
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

    /** Spoken-language choices besides "auto", where the model guesses, by ISO-639-1 code. */
    val LANGUAGES = linkedMapOf(
        "af" to "Afrikaans", "ar" to "Arabic", "hy" to "Armenian", "az" to "Azerbaijani",
        "be" to "Belarusian", "bn" to "Bengali", "bs" to "Bosnian", "bg" to "Bulgarian",
        "ca" to "Catalan", "zh" to "Chinese", "hr" to "Croatian", "cs" to "Czech",
        "da" to "Danish", "nl" to "Dutch", "en" to "English", "et" to "Estonian",
        "tl" to "Filipino", "fi" to "Finnish", "fr" to "French", "gl" to "Galician",
        "de" to "German", "el" to "Greek", "gu" to "Gujarati", "he" to "Hebrew",
        "hi" to "Hindi", "hu" to "Hungarian", "is" to "Icelandic", "id" to "Indonesian",
        "it" to "Italian", "ja" to "Japanese", "kn" to "Kannada", "kk" to "Kazakh",
        "ko" to "Korean", "lv" to "Latvian", "lt" to "Lithuanian", "mk" to "Macedonian",
        "ms" to "Malay", "ml" to "Malayalam", "mr" to "Marathi", "ne" to "Nepali",
        "no" to "Norwegian", "fa" to "Persian", "pl" to "Polish", "pt" to "Portuguese",
        "pa" to "Punjabi", "ro" to "Romanian", "ru" to "Russian", "sr" to "Serbian",
        "sk" to "Slovak", "sl" to "Slovenian", "es" to "Spanish", "sw" to "Swahili",
        "sv" to "Swedish", "ta" to "Tamil", "te" to "Telugu", "th" to "Thai",
        "tr" to "Turkish", "uk" to "Ukrainian", "ur" to "Urdu", "vi" to "Vietnamese",
        "cy" to "Welsh", "yi" to "Yiddish",
    )

    /*
     * Which picker languages each model accepts, from each company's published list
     * (checked 2026-11-06). MAI-Transcribe 2: Microsoft's table of 60 (Norwegian is "nb",
     * Filipino "fil"). Voxtral: the 45 named in Mistral's launch post (Mistral says 50 but
     * doesn't list them all). Scribe v2: ElevenLabs' list; it also took "yi" in testing.
     * GPT Transcribe: OpenAI publishes no list; tested 2026-11-05, it rejects Yiddish with
     * error 400 and writes Yiddish best with Hebrew set, so Yiddish sends "he".
     * If a model still refuses a language, the request is sent again without one (see transcribe).
     */
    private val MAI = setOf("af", "ar", "az", "bg", "bn", "bs", "ca", "cs", "da", "de", "el", "en", "es",
        "et", "fa", "fi", "tl", "fr", "gl", "gu", "he", "hi", "hu", "hy", "id", "is", "it", "ja", "kk", "kn",
        "ko", "lt", "lv", "mk", "ml", "mr", "ms", "no", "ne", "nl", "pa", "pl", "pt", "ro", "ru", "sk", "sl",
        "sv", "sw", "ta", "te", "th", "tr", "uk", "ur", "vi", "zh")
    private val VOXTRAL = setOf("ar", "bn", "bg", "ca", "zh", "cs", "da", "nl", "en", "et", "fi", "fr", "de",
        "el", "gu", "hi", "hu", "is", "id", "it", "ja", "kn", "ko", "lv", "lt", "mk", "ms", "ml", "mr", "no",
        "fa", "pl", "pt", "ro", "ru", "sk", "sl", "es", "sv", "tl", "te", "th", "tr", "uk", "vi")

    fun languagesFor(model: String): List<String> = LANGUAGES.keys.filter { languageCode(model, it) != null }

    /** The code sent to [model] for the picked [language], or null to let the model guess. */
    fun languageCode(model: String, language: String): String? {
        if (language !in LANGUAGES) return null
        return when (model) {
            "microsoft/mai-transcribe-2" -> when (language) {
                !in MAI -> null
                "no" -> "nb"
                "tl" -> "fil"
                else -> language
            }
            "mistralai/voxtral-mini-transcribe-3-0" -> language.takeIf { it in VOXTRAL }
            // Every picker language is on ElevenLabs' list.
            "elevenlabs/scribe-v2" -> if (language == "tl") "fil" else language
            "openai/gpt-transcribe" -> if (language == "yi") "he" else language
            else -> null
        }
    }

    /** Shown in the app for a model ID: the friendly name for a built-in model, the raw ID for a custom one. */
    /**
     * Errors where another model may still work: the model is missing (OpenRouter answers 400
     * "Model ... does not exist"), busy, timed out, or its company's servers failed. Not key,
     * credit, or recording-size errors, which hit every model.
     */
    fun shouldTryAnotherModel(code: Int?, detail: String? = null) =
        code == 404 || code == 408 || code == 429 || (code != null && code in 500..599) ||
        (code == 400 && detail?.contains("does not exist", ignoreCase = true) == true)

    /** The model to try when [failed] doesn't answer: the first built-in model that isn't [failed]. */
    fun fallbackFor(failed: String) = CLOUD_MODELS.first { it.id != failed }.id

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

    /**
     * With [deviceId] set (the Play build with no key entered, see FreeMinutes), the audio goes
     * to Word for Word's server, which uses its own OpenRouter key and counts free minutes.
     */
    fun transcribe(wavData: ByteArray, apiKey: String, model: String, verbatim: Boolean, language: String,
                   deviceId: String? = null, callback: (Result) -> Unit) {
        val viaServer = deviceId != null
        val builder = Request.Builder()
        if (viaServer) {
            builder.url(BuildConfig.SERVICE_URL + "/v1/transcriptions").header("X-Device-Id", deviceId!!)
        } else {
            builder.url("https://openrouter.ai/api/v1/audio/transcriptions")
                .header("Authorization", "Bearer $apiKey")
                .header("X-Title", "Word for Word")
        }
        val request = builder
            .post(buildBody(wavData, model, verbatim, language).toRequestBody("application/json".toMediaType()))
            .build()

        val languageSent = languageCode(model, language) != null
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) =
                callback(Result(null, if (viaServer) serverNetworkError(e) else networkError(e)))
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                if (languageSent && (response.code == 400 || response.code == 422) &&
                    !body.contains("does not exist")) {
                    // The model refused the language: send again and let it guess.
                    transcribe(wavData, apiKey, model, verbatim, "auto", deviceId, callback)
                } else if (response.isSuccessful) {
                    callback(parseResponse(body))
                } else {
                    val detail = parseResponse(body).error
                    val message = if (viaServer) serverError(response.code, body, detail, model)
                                  else httpError(response.code, detail, model)
                    callback(Result(null, message, response.code, detail))
                }
            }
        })
    }

    private fun serverNetworkError(e: IOException): String = when (e) {
        is java.net.UnknownHostException -> "No internet connection. Your words weren't sent."
        is java.net.SocketTimeoutException -> "The transcription service took too long to answer. Try again."
        else -> "Couldn't reach Word for Word's server (${e.message ?: "connection error"}). Try again."
    }

    /** Plain-language message for an error answer from Word for Word's server. */
    fun serverError(code: Int, body: String, detail: String?, model: String): String {
        val errorCode = try { JSONObject(body).getJSONObject("error").optString("code") } catch (e: Exception) { "" }
        return when {
            errorCode == "free_minutes_used" ->
                "You've used this month's free minutes. To keep going now, add your own OpenRouter key in Word for Word."
            // The server's OpenRouter key reached its monthly spending limit.
            code == 402 || code == 401 || code == 403 ->
                "Free minutes are paused right now. Try again later, or add your own OpenRouter key in Word for Word."
            else -> httpError(code, detail, model)
        }
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
