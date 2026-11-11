package com.kafkasl.phonewhisper

import android.content.Context
import android.util.Log

/**
 * One recording through the picked cloud model, and through another model when that one is busy
 * or down. Used for dictation and for audio files shared to the app (ShareActivity).
 */
object CloudTranscription {
    private const val TAG = "PhoneWhisper"
    const val NO_KEY = "Set your OpenRouter API key in the Word for Word app"

    /**
     * [text] is null when no words came back; [failure] is then the answer to report.
     * [unavailable] is the picked model when the backup model wrote the text instead.
     */
    class Outcome(val text: String?, val model: String, val unavailable: String?, val failure: TranscriberClient.Result?) {
        /** Says which model answered, when it wasn't the picked one. */
        val notice get() = unavailable?.let {
            "${TranscriberClient.titleFor(it)} was unavailable. Used ${TranscriberClient.titleFor(model)}."
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences("phonewhisper", Context.MODE_PRIVATE)

    /** False when there is no OpenRouter key and this build has no free minutes. */
    fun ready(context: Context) =
        !prefs(context).getString("api_key", "").isNullOrBlank() || FreeMinutes.inUse(context)

    /** [wav] is 16 kHz, 16-bit mono, as WavWriter makes it. */
    fun run(context: Context, wav: ByteArray, callback: (Outcome) -> Unit) {
        val p = prefs(context)
        val apiKey = p.getString("api_key", "") ?: ""
        // The Play build with no key entered uses Word for Word's server and its free minutes.
        val deviceId = if (FreeMinutes.inUse(context)) FreeMinutes.deviceId(context) else null
        // With free minutes, Word for Word picks the model; the picker is hidden.
        val model = if (deviceId != null) TranscriberClient.DEFAULT_MODEL
                    else p.getString("cloud_model", TranscriberClient.DEFAULT_MODEL) ?: TranscriberClient.DEFAULT_MODEL
        val verbatim = p.getBoolean("verbatim", false)
        // With Yiddish picked, Yiddish audio goes to the Yiddish model; anything else lets the model guess.
        val language = (p.getString("language", "auto") ?: "auto").let { if (it == "yi") "auto" else it }
        // Audio length to a tenth of a second (32,000 bytes a second after the 44-byte header), for usage stats.
        val seconds = Math.round((wav.size - 44) / 3200.0) / 10.0

        fun done(text: String, used: String) {
            Analytics.track("transcribed", mapOf("model" to used, "seconds" to seconds,
                "backup_model" to (used != model), "free_minutes" to (deviceId != null)))
            callback(Outcome(text, used, model.takeIf { it != used }, null))
        }

        TranscriberClient.transcribe(wav, apiKey, model, verbatim, language, deviceId) { result ->
            if (!result.text.isNullOrBlank()) {
                done(result.text, model)
            } else if (TranscriberClient.shouldTryAnotherModel(result.code, result.detail)) {
                // The chosen model is busy or down: send the same recording to another model right away.
                val backup = TranscriberClient.fallbackFor(model)
                Log.i(TAG, "$model failed (${result.code}), trying $backup")
                TranscriberClient.transcribe(wav, apiKey, backup, verbatim, language, deviceId) { second ->
                    Log.i(TAG, "$backup answered: ${if (!second.text.isNullOrBlank()) "text" else second.code?.let { "error $it" } ?: "no text"}")
                    if (!second.text.isNullOrBlank()) done(second.text, backup)
                    else callback(Outcome(null, model, null, if (second.error == null && second.text != null) second else result))
                }
            } else {
                callback(Outcome(null, model, null, result))
            }
        }
    }

    /** Counts a failed transcription. True when the model answered but heard no words. */
    fun trackFailure(model: String, result: TranscriberClient.Result): Boolean {
        val heardNothing = result.error == null && result.text != null
        Analytics.track("transcription_failed", mapOf(
            "model" to model,
            "reason" to if (heardNothing) "no_words" else "error",
            "http_code" to (result.code ?: 0)))
        return heardNothing
    }
}
