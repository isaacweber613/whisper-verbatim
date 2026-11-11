package com.kafkasl.phonewhisper

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import kotlin.concurrent.thread

/**
 * Isaac (2026-11-11): share an audio file (a voice note, a recording) to Word for Word from any
 * app. It is transcribed with the same model as dictation, copied to the clipboard and saved to
 * History. With Yiddish picked as the language it first asks if the file is Yiddish; yes sends it
 * to the Yiddish model on Isaac's PC like a Yiddish take, marked "app-share" there because the
 * voice in a shared file is often someone else's.
 */
class ShareActivity : AppCompatActivity() {
    private var dialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = if (intent?.action == Intent.ACTION_SEND)
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) else null
        if (uri == null) { fail("Couldn't read this file as audio."); return }
        if (YiddishServer.enabled(prefs())) {
            show(AlertDialog.Builder(this)
                .setMessage("Is this recording in Yiddish?")
                .setPositiveButton("Yiddish") { _, _ ->
                    if (YiddishServer.needsConsent(prefs())) askYiddishConsent(uri) else decode(uri, yiddish = true)
                }
                .setNegativeButton("Not Yiddish") { _, _ -> decode(uri, yiddish = false) })
        } else {
            decode(uri, yiddish = false)
        }
    }

    override fun onDestroy() {
        dialog?.dismiss()
        super.onDestroy()
    }

    /** Play build only (see [YiddishServer.needsConsent]). "No thanks" still transcribes, with the normal model. */
    private fun askYiddishConsent(uri: Uri) {
        show(AlertDialog.Builder(this)
            .setTitle("Yiddish training")
            .setMessage(YiddishServer.DISCLOSURE)
            .setPositiveButton("Agree") { _, _ ->
                prefs().edit().putBoolean(YiddishServer.CONSENT, true).apply()
                decode(uri, yiddish = true)
            }
            .setNegativeButton("No thanks") { _, _ -> decode(uri, yiddish = false) })
    }

    private fun decode(uri: Uri, yiddish: Boolean) {
        showProgress()
        thread {
            val pcm = try { AudioDecoder.decode(this, uri) } catch (e: Exception) {
                Log.w(TAG, "Couldn't decode the shared file", e)
                null
            }
            ui {
                if (pcm == null || pcm.isEmpty()) fail("Couldn't read this file as audio.")
                else WavWriter.encode(pcm).let { if (yiddish) transcribeYiddish(it) else transcribeCloud(it, null) }
            }
        }
    }

    private fun transcribeYiddish(wav: ByteArray) {
        showProgress()
        val install = YiddishServer.installId(this)
        YiddishServer.transcribe(wav, BuildConfig.VERSION_NAME, install, "app-share") { result, id ->
            if (!result.text.isNullOrBlank()) {
                Analytics.track("transcribed", mapOf("model" to "yiddish-pc",
                    "seconds" to Math.round((wav.size - 44) / 3200.0) / 10.0, "backup_model" to false, "free_minutes" to false))
                // Nothing is typed into a text box, so there are no edits to follow.
                id?.let { YiddishServer.sendCorrection(it, null, "not_tracked", BuildConfig.VERSION_NAME, install, false) }
                ui { finishWith(result.text, "yiddish-pc", null) }
            } else if (YiddishServer.isOff(result)) {
                ui { transcribeCloud(wav, YiddishServer.OFF_NOTICE) }
            } else {
                val heardNothing = CloudTranscription.trackFailure("yiddish-pc", result)
                ui { if (heardNothing) fail(NO_WORDS) else fail(result.error ?: NO_WORDS) { transcribeYiddish(wav) } }
            }
        }
    }

    /** [notice] is shown with the result, for example that the Yiddish model was off. */
    private fun transcribeCloud(wav: ByteArray, notice: String?) {
        if (!CloudTranscription.ready(this)) { fail(CloudTranscription.NO_KEY); return }
        showProgress()
        CloudTranscription.run(this, wav) { o ->
            if (o.text != null) {
                ui { finishWith(o.text, o.model, notice ?: o.notice) }
            } else {
                val heardNothing = CloudTranscription.trackFailure(o.model, o.failure!!)
                ui { if (heardNothing) fail(NO_WORDS) else fail(o.failure.error ?: NO_WORDS) { transcribeCloud(wav, notice) } }
            }
        }
    }

    private fun finishWith(text: String, model: String, notice: String?) {
        try { HistoryStore.add(this, text, model) } catch (e: Exception) { Log.w(TAG, "Couldn't save to history", e) }
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("Word for Word", text))
        // Android 13 and newer show their own "copied" preview.
        val message = when {
            notice != null -> notice
            Build.VERSION.SDK_INT < 33 -> "Copied to clipboard"
            else -> null
        }
        message?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
        finish()
    }

    /** [retry] adds a "Try again" button, for failures where sending the same audio again can work. */
    private fun fail(message: String, retry: (() -> Unit)? = null) {
        val b = AlertDialog.Builder(this).setMessage(message).setNegativeButton("Close") { _, _ -> finish() }
        retry?.let { b.setPositiveButton("Try again") { _, _ -> it() } }
        show(b)
    }

    private fun showProgress() {
        val pad = (24 * resources.displayMetrics.density).toInt()
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(ProgressBar(context))
            addView(TextView(context).apply {
                text = "Transcribing..."
                textSize = 16f
                setPadding(pad, 0, 0, 0)
            })
        }
        show(AlertDialog.Builder(this).setView(row))
    }

    /** One dialog at a time. Backing out of any of them closes the screen and drops the result. */
    private fun show(builder: AlertDialog.Builder) {
        dialog?.dismiss()
        dialog = builder.setOnCancelListener { finish() }.create().apply {
            setCanceledOnTouchOutside(false)
            show()
        }
    }

    private fun ui(block: () -> Unit) = runOnUiThread { if (!isFinishing && !isDestroyed) block() }

    private fun prefs() = getSharedPreferences("phonewhisper", MODE_PRIVATE)

    private companion object {
        const val TAG = "PhoneWhisper"
        const val NO_WORDS = "No words came back from this file."
    }
}
