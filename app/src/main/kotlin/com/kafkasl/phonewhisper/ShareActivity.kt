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
import java.io.File
import java.io.RandomAccessFile
import kotlin.concurrent.thread

/**
 * Isaac (2026-11-11): share an audio file (a voice note, a recording) to Word for Word from any
 * app. It is transcribed with the same model as dictation, copied to the clipboard and saved to
 * History. With Yiddish picked as the language it first asks if the file is Yiddish; yes sends it
 * to the Yiddish model on Isaac's PC like a Yiddish take, marked "app-share" there because the
 * voice in a shared file is often someone else's. A long file is sent in pieces of about 5
 * minutes, one after another (see [AudioSplitter]).
 */
class ShareActivity : AppCompatActivity() {
    private var dialog: AlertDialog? = null
    /** The label of the progress dialog while it is showing. */
    private var progress: TextView? = null
    /** The whole file as 16 kHz mono PCM, kept in the app's cache until this screen closes. */
    @Volatile private var pcm: File? = null
    /** How many bytes of [pcm] are transcribed, and the text of those pieces. */
    private var done = 0L
    private val texts = mutableListOf<String>()
    /** The rest of the file goes to the Yiddish model. Turns off if the PC is off. */
    @Volatile private var yiddish = false
    private var model = ""
    /** Shown with the result, for example that the Yiddish model was off. */
    @Volatile private var notice: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = if (intent?.action == Intent.ACTION_SEND)
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) else null
        if (uri == null) { fail(NOT_AUDIO); return }
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
        pcm?.delete()
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
        this.yiddish = yiddish
        showProgress()
        thread {
            val ok = try {
                val file = File.createTempFile("pcm", null, cacheDir).also { pcm = it }
                AudioDecoder.decode(this, uri, file)
                file.length() > 0
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't decode the shared file", e)
                false
            }
            ui { if (ok) nextPiece() else fail(NOT_AUDIO) }
        }
    }

    /** Sends the next piece of the file, or finishes when there is none left. */
    private fun nextPiece() {
        val file = pcm ?: return
        if (done >= file.length()) { finishWith(); return }
        showProgress()
        val start = done
        thread {
            val piece = try {
                RandomAccessFile(file, "r").use { f ->
                    ByteArray(AudioSplitter.pieceSize(f, start)).also { f.seek(start); f.readFully(it) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't read the next piece", e)
                null
            }
            if (piece == null) ui { fail(NOT_AUDIO) } else send(piece)
        }
    }

    private fun send(piece: ByteArray) {
        val wav = WavWriter.encode(piece)
        if (!yiddish) { sendCloud(piece, wav); return }
        val install = YiddishServer.installId(this)
        YiddishServer.transcribe(wav, BuildConfig.VERSION_NAME, install, "app-share") { result, id ->
            if (!result.text.isNullOrBlank()) {
                Analytics.track("transcribed", mapOf("model" to "yiddish-pc",
                    "seconds" to Math.round(piece.size / 3200.0) / 10.0, "backup_model" to false, "free_minutes" to false))
                // Nothing is typed into a text box, so there are no edits to follow.
                id?.let { YiddishServer.sendCorrection(it, null, "not_tracked", BuildConfig.VERSION_NAME, install, false) }
                ui { pieceDone(piece, result.text, "yiddish-pc") }
            } else if (YiddishServer.isOff(result)) {
                yiddish = false
                notice = YiddishServer.OFF_NOTICE
                sendCloud(piece, wav)
            } else {
                pieceFailed(piece, "yiddish-pc", result)
            }
        }
    }

    private fun sendCloud(piece: ByteArray, wav: ByteArray) {
        if (!CloudTranscription.ready(this)) { ui { fail(CloudTranscription.NO_KEY) }; return }
        CloudTranscription.run(this, wav) { o ->
            if (o.text != null) {
                if (notice == null) notice = o.notice
                ui { pieceDone(piece, o.text, o.model) }
            } else {
                pieceFailed(piece, o.model, o.failure!!)
            }
        }
    }

    private fun pieceFailed(piece: ByteArray, model: String, result: TranscriberClient.Result) {
        val heardNothing = CloudTranscription.trackFailure(model, result)
        ui {
            // A piece with no words in it (a pause, music) is skipped; the rest still goes.
            if (heardNothing) pieceDone(piece, null, model)
            else fail(result.error ?: NO_WORDS) { showProgress(); thread { send(piece) } }
        }
    }

    private fun pieceDone(piece: ByteArray, text: String?, model: String) {
        if (text != null) {
            texts += text.trim()
            this.model = model
        }
        done += piece.size
        nextPiece()
    }

    private fun finishWith() {
        if (texts.isEmpty()) { fail(NO_WORDS); return }
        val text = texts.joinToString(" ")
        try { HistoryStore.add(this, text, model) } catch (e: Exception) { Log.w(TAG, "Couldn't save to history", e) }
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("Word for Word", text))
        // Android 13 and newer show their own "copied" preview.
        val message = notice ?: if (Build.VERSION.SDK_INT < 33) "Copied to clipboard" else null
        message?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
        finish()
    }

    /**
     * [retry] adds a "Try again" button, for failures where sending the same audio again can work.
     * Closing after part of a long file was transcribed keeps that part.
     */
    private fun fail(message: String, retry: (() -> Unit)? = null) {
        val close = {
            if (texts.isEmpty()) finish()
            else { notice = "Only the first part was transcribed."; finishWith() }
        }
        val b = AlertDialog.Builder(this).setMessage(message).setNegativeButton("Close") { _, _ -> close() }
        retry?.let { b.setPositiveButton("Try again") { _, _ -> it() } }
        show(b, close)
    }

    /** After the first piece of a long file it also says how far along it is. */
    private fun showProgress() {
        val total = pcm?.length() ?: 0L
        val label = if (done > 0 && total > 0) "Transcribing... ${done * 100 / total}%" else "Transcribing..."
        progress?.let { it.text = label; return }
        val pad = (24 * resources.displayMetrics.density).toInt()
        val labelView = TextView(this).apply {
            text = label
            textSize = 16f
            setPadding(pad, 0, 0, 0)
        }
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(ProgressBar(context))
            addView(labelView)
        }
        show(AlertDialog.Builder(this).setView(row))
        progress = labelView
    }

    /** One dialog at a time. Backing out of a question or of the progress closes the screen and drops the result. */
    private fun show(builder: AlertDialog.Builder, onCancel: () -> Unit = { finish() }) {
        dialog?.dismiss()
        progress = null
        dialog = builder.setOnCancelListener { onCancel() }.create().apply {
            setCanceledOnTouchOutside(false)
            show()
        }
    }

    private fun ui(block: () -> Unit) = runOnUiThread { if (!isFinishing && !isDestroyed) block() }

    private fun prefs() = getSharedPreferences("phonewhisper", MODE_PRIVATE)

    private companion object {
        const val TAG = "PhoneWhisper"
        const val NO_WORDS = "No words came back from this file."
        const val NOT_AUDIO = "Couldn't read this file as audio."
    }
}
