package com.kafkasl.phonewhisper

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.io.ByteArrayOutputStream
import kotlin.concurrent.thread
import kotlin.math.abs

class WhisperAccessibilityService : AccessibilityService() {

    companion object {
        var instance: WhisperAccessibilityService? = null
        /** Called on the main thread when the service turns on or off, so the open app can update right away. */
        var onStateChanged: (() -> Unit)? = null
        /** True while the app's "set shortcut" dialog listens for keys, so they reach it. */
        var capturingShortcut = false
        private const val TAG = "PhoneWhisper"
        private const val SAMPLE_RATE = 16000
        private const val BTN_DP = 44
        private const val PAD_DP = 10
        private const val MARGIN_DP = 8
        private const val TAP_THRESHOLD_DP = 10
        private const val RING_DP = 56
        private const val FEEDBACK_OFFSET_DP = 64

        private const val COLOR_IDLE = 0xDD1C1C1E.toInt()
        private const val COLOR_RECORDING = 0xDD4F46E5.toInt()
        private const val COLOR_BUSY = 0xDD6B6B6B.toInt()
        private const val COLOR_RETRY = 0xDDF59E0B.toInt()
        /** Recording a Yiddish take (sent to the Yiddish model on Isaac's PC). */
        private const val COLOR_YIDDISH = 0xDD0EA5E9.toInt()
        private const val COLOR_FEEDBACK_BG = 0xEE1C1C1E.toInt()
        private const val COLOR_RING = 0xFFE8EAED.toInt()
        private const val NO_WORDS = "No words came back. Try speaking a little louder."

        /** WhatsApp's message box doesn't always flag its "Message" placeholder. */
        private val BUILT_IN_PLACEHOLDERS = setOf(
            "com.whatsapp/com.whatsapp:id/entry|Message",
            "com.whatsapp.w4b/com.whatsapp.w4b:id/entry|Message",
        )
    }

    /** RETRY: a recording failed to transcribe and is kept. Tap sends it again, hold discards it. */
    private enum class State { IDLE, RECORDING, TRANSCRIBING, RETRY }

    private var state = State.IDLE
    /** The recording that failed to transcribe, kept so a tap can send it again. */
    private var failedPcm: ByteArray? = null
    /** The current (or failed) take goes to the Yiddish model; see [YiddishServer]. */
    private var yiddishTake = false
    /** Id of the Yiddish take about to be typed; [trySetText] turns it into a [PendingEdit]. */
    private var typingYiddishId: String? = null

    /**
     * Isaac (2026-11-08): when someone edits the words a Yiddish take typed, the edited words go back
     * to the PC as that take's correct text. The box's text before and after the typed words is
     * remembered; while the box still starts and ends with them, whatever is in between is the
     * edited take. It's sent when the box no longer matches (sent or cleared), when the next
     * recording starts, or after 2 minutes, and only if it changed.
     */
    private class PendingEdit(val id: String, val prefix: String, val suffix: String, val original: String) {
        var latest = original
    }
    private var pendingEdit: PendingEdit? = null
    private val finishEditRunnable = Runnable { finishEdit() }

    private fun watchEdit(event: AccessibilityEvent) {
        val p = pendingEdit ?: return
        // Leaving the screen ends the edit with the words as they are.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) { finishEdit(); return }
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return
        val src = event.source
        // An emptied box reports its grey hint ("First name", "Message") as text; that counts as empty.
        val t = if (src != null && isPlaceholder(src)) "" else (src?.text ?: event.text?.joinToString("") ?: return).toString()
        Log.i(TAG, "Edit watch: box now ${t.length} chars")
        val seg = if (t.length >= p.prefix.length + p.suffix.length && t.startsWith(p.prefix) && t.endsWith(p.suffix))
            t.substring(p.prefix.length, t.length - p.suffix.length).trim() else ""
        // An emptied box (message sent) or one that no longer matches ends the edit with the last good words.
        if (seg.isNotBlank()) p.latest = seg else finishEdit()
    }

    private fun finishEdit() {
        handler.removeCallbacks(finishEditRunnable)
        val p = pendingEdit ?: return
        pendingEdit = null
        // Words cut to under half were being deleted (backspace held down), not corrected.
        val changed = p.latest.isNotBlank() && p.latest != p.original && p.latest.length * 2 >= p.original.length
        Log.i(TAG, "Edit watch ended for ${p.id}: ${if (changed) "sending the edited words" else "no change"}")
        if (changed) YiddishServer.sendCorrection(p.id, p.latest)
    }
    private var overlayView: FrameLayout? = null
    private var button: ImageView? = null
    private var bars: LevelBarsView? = null
    private var spinner: ProgressBar? = null
    private var feedbackView: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var feedbackLayoutParams: WindowManager.LayoutParams? = null
    private var audioRecord: AudioRecord? = null
    private var pcmStream: ByteArrayOutputStream? = null
    private val handler = Handler(Looper.getMainLooper())
    private val hideFeedback = Runnable {
        feedbackView?.animate()?.alpha(0f)?.setDuration(180)?.withEndAction {
            feedbackView?.visibility = View.GONE
        }?.start()
    }

    /** Set when the keyboard shortcut is on; the mic button then shows only while you talk. */
    private var shortcut: ShortcutMatcher? = null
    private val shortcutHeld = Runnable {
        if (shortcut?.holdElapsed(android.os.SystemClock.uptimeMillis()) == true) onTap("shortcut")
    }
    /** The second shortcut, for Yiddish takes; only set while Yiddish is the picked language. */
    private var shortcutYiddish: ShortcutMatcher? = null
    private val shortcutYiddishHeld = Runnable {
        if (shortcutYiddish?.holdElapsed(android.os.SystemClock.uptimeMillis()) == true) onTap("shortcut-yiddish", yiddish = true)
    }
    /** Keeps the button up while a "couldn't type here" message shows. */
    private var showingPasteFailure = false
    private val endPasteFailure = Runnable { showingPasteFailure = false; scheduleVisibilityUpdate() }

    // Local transcription engine (loaded lazily)
    private var localTranscriber: LocalTranscriber? = null

    private val dp get() = resources.displayMetrics.density
    private val screenW get() = resources.displayMetrics.widthPixels
    private val screenH get() = resources.displayMetrics.heightPixels

    override fun onServiceConnected() {
        instance = this
        handler.post { onStateChanged?.invoke() }
        showOverlay()
        applyShortcutSettings()
        // Debug builds only: lets tests type text as if it were dictated, since emulators have no voice.
        //   adb shell am broadcast -a com.isaacweber.wordforword.DEBUG_DICTATE --es text "hello"
        if (BuildConfig.DEBUG) {
            androidx.core.content.ContextCompat.registerReceiver(this, object : android.content.BroadcastReceiver() {
                override fun onReceive(c: Context, i: android.content.Intent) {
                    // --es yiddishId <take id> also tests sending edits back for that Yiddish take.
                    typingYiddishId = i.getStringExtra("yiddishId")
                    handleTranscriptionResult(i.getStringExtra("text"), "debug")
                }
            }, android.content.IntentFilter("com.isaacweber.wordforword.DEBUG_DICTATE"),
                androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
        }
        // Try to load local model in background
        thread { initLocalModel() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let { try { watchEdit(it) } catch (_: Exception) {} }
        // Text changes are only for following edits of a Yiddish take; they don't change what's on screen.
        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return
        // When you tap into an empty box, learn its placeholder so it never gets typed later.
        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
            event.source?.let { src ->
                try { if (src.isEditable) isPlaceholder(src) } catch (_: Exception) {}
            }
        }
        // Windows (including the keyboard) opened, closed, or focus moved. Re-check
        // after a short pause so a burst of events causes one update.
        scheduleVisibilityUpdate()
    }
    override fun onInterrupt() {}

    // --- Keyboard shortcut ---

    /** Reads the shortcut settings; called on start and whenever the app changes them. */
    fun applyShortcutSettings() {
        val p = prefs()
        val on = Shortcut.enabled(p)
        shortcut = if (on) ShortcutMatcher(Shortcut.keys(p), Shortcut.holdMs(p), Shortcut.blocksKey(p)) else null
        shortcutYiddish = if (on && YiddishServer.enabled(p) && Shortcut.yiddishKeys(p).isNotEmpty())
            ShortcutMatcher(Shortcut.yiddishKeys(p), Shortcut.holdMs(p)) else null
        handler.removeCallbacks(shortcutHeld)
        handler.removeCallbacks(shortcutYiddishHeld)
        // Ask Android for key presses only while the shortcut is on.
        serviceInfo = serviceInfo?.apply {
            val f = android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            flags = if (on) flags or f else flags and f.inv()
        }
        scheduleVisibilityUpdate()
    }

    /** Returns true only to hide a blocked one-key shortcut from apps; other keys reach the app as usual. */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        val m = shortcut ?: return false
        if (capturingShortcut) return false
        val y = shortcutYiddish
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                when (m.onDown(event.keyCode, event.repeatCount, event.eventTime)) {
                    ShortcutMatcher.Result.FIRE -> onTap("shortcut")
                    ShortcutMatcher.Result.WAIT -> {
                        handler.removeCallbacks(shortcutHeld)
                        handler.postDelayed(shortcutHeld, Shortcut.holdMs(prefs()))
                    }
                    ShortcutMatcher.Result.NONE -> Unit
                }
                when (y?.onDown(event.keyCode, event.repeatCount, event.eventTime)) {
                    ShortcutMatcher.Result.FIRE -> onTap("shortcut-yiddish", yiddish = true)
                    ShortcutMatcher.Result.WAIT -> {
                        handler.removeCallbacks(shortcutYiddishHeld)
                        handler.postDelayed(shortcutYiddishHeld, Shortcut.holdMs(prefs()))
                    }
                    else -> Unit
                }
            }
            KeyEvent.ACTION_UP -> { m.onUp(event.keyCode, event.eventTime); y?.onUp(event.keyCode, event.eventTime) }
        }
        return m.blocks(event.keyCode)
    }

    // --- Show the button only while the keyboard is open ---

    private val updateVisibility = Runnable { applyVisibility() }

    private fun scheduleVisibilityUpdate() {
        handler.removeCallbacks(updateVisibility)
        handler.postDelayed(updateVisibility, 120)
    }

    /** True when the on-screen keyboard is showing, which means a text box has focus. */
    private fun keyboardVisible(): Boolean = try {
        windows?.any { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD } == true
    } catch (e: Exception) {
        false
    }

    private fun applyVisibility() {
        val overlay = overlayView ?: return
        val params = layoutParams ?: return
        // Never hide mid-recording or mid-transcription, even if the keyboard closes.
        // With the keyboard shortcut on, the button shows only while you talk, not with the keyboard.
        val busy = state == State.RECORDING || state == State.TRANSCRIBING
        val show = if (shortcut != null) busy || state == State.RETRY || showingPasteFailure
                   else busy || keyboardVisible()
        val wasShown = overlay.visibility == View.VISIBLE
        if (show == wasShown) return
        overlay.visibility = if (show) View.VISIBLE else View.GONE
        // A hidden window still catches taps unless it's marked not touchable.
        params.flags = if (show) params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                       else params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).updateViewLayout(overlay, params)
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't update overlay visibility", e)
        }
    }

    override fun onDestroy() {
        instance = null
        handler.post { onStateChanged?.invoke() }
        removeOverlay()
        super.onDestroy()
    }

    private fun initLocalModel() {
        val modelName = prefs().getString("model_name", "") ?: ""
        if (modelName.isBlank()) {
            // Auto-detect first available model
            val models = LocalTranscriber.availableModels(this)
            if (models.isNotEmpty()) {
                Log.i(TAG, "Auto-detected model: ${models.first()}")
                localTranscriber = LocalTranscriber.create(this, models.first())
            }
        } else {
            localTranscriber = LocalTranscriber.create(this, modelName)
        }
        if (localTranscriber != null) {
            Log.i(TAG, "Local transcription ready")
        } else {
            Log.i(TAG, "No local model found, will use API")
        }
    }

    /** Reload local model (called from MainActivity when settings change) */
    fun reloadModel() { thread { initLocalModel() } }

    // --- Overlay ---

    private fun showOverlay() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val buttonSize = (BTN_DP * dp).toInt()
        val ringSize = (RING_DP * dp).toInt()
        val pad = (PAD_DP * dp).toInt()
        val margin = (MARGIN_DP * dp).toInt()

        val ring = ProgressBar(this).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(COLOR_RING)
            visibility = View.GONE
        }

        val img = ImageView(this).apply {
            setImageResource(R.drawable.ic_mic)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(pad, pad, pad, pad)
            background = circle(COLOR_IDLE)
        }

        val levelBars = LevelBarsView(this).apply { visibility = View.GONE }
        val barsSize = (buttonSize * 0.6f).toInt()

        val overlay = FrameLayout(this).apply {
            addView(ring, FrameLayout.LayoutParams(ringSize, ringSize, Gravity.CENTER))
            addView(img, FrameLayout.LayoutParams(buttonSize, buttonSize, Gravity.CENTER))
            addView(levelBars, FrameLayout.LayoutParams(barsSize, barsSize, Gravity.CENTER))
        }

        val params = WindowManager.LayoutParams(
            ringSize, ringSize,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenW - ringSize - margin
            y = screenH / 2 - ringSize / 2
        }

        var startX = 0; var startY = 0
        var touchX = 0f; var touchY = 0f
        var discarded = false
        // Holding the button while a failed recording waits discards it.
        val holdToDiscard = Runnable {
            if (state == State.RETRY) { discarded = true; discardFailed() }
        }
        // With Yiddish picked as the language, holding the idle button records a Yiddish take.
        var heldForYiddish = false
        val holdForYiddish = Runnable {
            if (state == State.IDLE && YiddishServer.enabled(prefs())) { heldForYiddish = true; onTap("button-hold", yiddish = true) }
        }

        overlay.setOnTouchListener { v, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x; startY = params.y
                    touchX = ev.rawX; touchY = ev.rawY
                    discarded = false
                    heldForYiddish = false
                    if (state == State.RETRY) {
                        handler.postDelayed(holdToDiscard, android.view.ViewConfiguration.getLongPressTimeout().toLong())
                    } else if (state == State.IDLE && YiddishServer.enabled(prefs())) {
                        handler.postDelayed(holdForYiddish, android.view.ViewConfiguration.getLongPressTimeout().toLong())
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(ev.rawX - touchX) + abs(ev.rawY - touchY) >= TAP_THRESHOLD_DP * dp) {
                        handler.removeCallbacks(holdToDiscard)
                        handler.removeCallbacks(holdForYiddish)
                    }
                    params.x = startX + (ev.rawX - touchX).toInt()
                    params.y = startY + (ev.rawY - touchY).toInt()
                    wm.updateViewLayout(v, params)
                    feedbackLayoutParams?.let {
                        positionFeedback(it, params)
                        wm.updateViewLayout(feedbackView, it)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(holdToDiscard)
                    handler.removeCallbacks(holdForYiddish)
                    val moved = abs(ev.rawX - touchX) + abs(ev.rawY - touchY)
                    if (discarded || heldForYiddish) {
                        // The hold already discarded the recording or started a Yiddish take; this isn't a tap.
                    } else if (moved < TAP_THRESHOLD_DP * dp) {
                        onTap("button")
                    } else {
                        params.x = if (params.x + ringSize / 2 > screenW / 2)
                            screenW - ringSize - margin else margin
                        wm.updateViewLayout(v, params)
                        feedbackLayoutParams?.let {
                            positionFeedback(it, params)
                            wm.updateViewLayout(feedbackView, it)
                        }
                    }
                    true
                }
                else -> false
            }
        }

        val feedback = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
            background = pill(COLOR_FEEDBACK_BG)
            maxWidth = screenW - (32 * dp).toInt()
            alpha = 0f
            visibility = View.GONE
        }

        val feedbackParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        positionFeedback(feedbackParams, params)

        wm.addView(overlay, params)
        wm.addView(feedback, feedbackParams)
        overlayView = overlay
        button = img
        bars = levelBars
        spinner = ring
        feedbackView = feedback
        layoutParams = params
        feedbackLayoutParams = feedbackParams

        // Start hidden; applyVisibility shows it once the keyboard is up.
        overlay.visibility = View.GONE
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        wm.updateViewLayout(overlay, params)
        scheduleVisibilityUpdate()
    }

    private fun removeOverlay() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView?.let {
            wm.removeView(it)
            overlayView = null
        }
        feedbackView?.let {
            wm.removeView(it)
            feedbackView = null
        }
        button = null
        bars = null
        spinner = null
        layoutParams = null
        feedbackLayoutParams = null
    }

    private fun circle(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL; setColor(color)
    }

    private fun pill(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 16 * dp
        setColor(color)
    }

    private fun setAppearance(color: Int) {
        handler.post {
            button?.background = circle(color)
            button?.setImageResource(if (state == State.RETRY) R.drawable.ic_retry else R.drawable.ic_mic)
            applyKeepScreenOn()
        }
        // Every state change goes through here; once back to idle, hide again if the keyboard closed.
        scheduleVisibilityUpdate()
    }

    /** The screen doesn't time out while you dictate or while your words are on their way. */
    private fun applyKeepScreenOn() {
        val overlay = overlayView ?: return
        val params = layoutParams ?: return
        val on = state == State.RECORDING || state == State.TRANSCRIBING
        val flag = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        val flags = if (on) params.flags or flag else params.flags and flag.inv()
        if (flags == params.flags) return
        params.flags = flags
        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).updateViewLayout(overlay, params)
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't update keep-screen-on", e)
        }
    }

    private fun setBusy(visible: Boolean) {
        handler.post {
            spinner?.visibility = if (visible) View.VISIBLE else View.GONE
        }
    }

    private fun positionFeedback(
        feedbackParams: WindowManager.LayoutParams,
        bubbleParams: WindowManager.LayoutParams
    ) {
        // Centered across the screen, just above the button, so longer messages never run off the edge.
        val margin = (MARGIN_DP * dp).toInt()
        feedbackParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        feedbackParams.x = 0
        feedbackParams.y = maxOf(margin, bubbleParams.y - (FEEDBACK_OFFSET_DP * dp).toInt())
    }

    private fun showFeedback(text: String, durationMs: Long = 2000) {
        handler.post {
            val view = feedbackView ?: return@post
            val bubbleParams = layoutParams ?: return@post
            val feedbackParams = feedbackLayoutParams ?: return@post
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager

            view.text = text
            positionFeedback(feedbackParams, bubbleParams)
            wm.updateViewLayout(view, feedbackParams)

            handler.removeCallbacks(hideFeedback)
            view.animate().cancel()
            view.visibility = View.VISIBLE
            view.alpha = 0f
            view.animate().alpha(1f).setDuration(120).start()
            handler.postDelayed(hideFeedback, durationMs)
        }
    }

    /** Swaps the mic icon for bars that move with your voice. */
    // 25 ms: Isaac (2026-11-06) found the first 60 ms too long and asked for less than half.
    private val BUZZ_MS = 25L

    /** A short buzz when recording starts or stops, from the button or the shortcut (setting "vibrate", on by default). */
    private fun buzz() {
        if (!prefs().getBoolean("vibrate", true)) return
        try {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as? android.os.Vibrator ?: return
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(BUZZ_MS, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION") vibrator.vibrate(BUZZ_MS)
            }
        } catch (_: Exception) {
        }
    }

    private fun startPulse() {
        handler.post {
            button?.setImageDrawable(null)
            bars?.start()
        }
    }

    private fun stopPulse() {
        handler.post {
            bars?.stop()
            button?.setImageResource(R.drawable.ic_mic)
        }
    }

    /** Loudness of a chunk of 16-bit audio, scaled so normal speech fills most of the bars. */
    private fun levelOf(buf: ByteArray, n: Int): Float {
        var sum = 0.0
        val samples = n / 2
        if (samples == 0) return 0f
        for (i in 0 until samples) {
            val s = ((buf[i * 2 + 1].toInt() shl 8) or (buf[i * 2].toInt() and 0xFF)).toShort().toInt()
            sum += s.toDouble() * s
        }
        val rms = kotlin.math.sqrt(sum / samples)
        // Map roughly -50 dB (room noise) .. -10 dB (loud speech) onto 0..1.
        val db = 20 * kotlin.math.log10(rms / 32768.0 + 1e-9)
        return ((db + 50) / 40).toFloat().coerceIn(0f, 1f)
    }

    // --- State machine ---

    /** [via] is "button" or "shortcut", for the anonymous usage counts. [yiddish] starts a Yiddish take. */
    private fun onTap(via: String, yiddish: Boolean = false) {
        when (state) {
            State.IDLE -> startRecording(via, yiddish)
            State.RECORDING -> stopAndTranscribe()
            State.TRANSCRIBING -> toast("Still transcribing. One moment.")
            State.RETRY -> retry()
        }
    }

    /** Keeps the recording and turns the button into a retry button. */
    private fun failWithRetry(pcm: ByteArray, msg: String) {
        handler.post {
            failedPcm = pcm
            state = State.RETRY
            setBusy(false)
            setAppearance(COLOR_RETRY)
            showFeedback(if (shortcut != null) "$msg\nPress the shortcut to try again." else "$msg\nTap to try again. Hold to discard.", 6000)
        }
    }

    private fun retry() {
        val pcm = failedPcm ?: run { reset("Nothing to retry."); return }
        failedPcm = null
        state = State.TRANSCRIBING
        setAppearance(COLOR_BUSY)
        setBusy(true)
        showFeedback("Trying again...", 1200)
        if (yiddishTake) transcribeYiddish(pcm) else transcribeApi(pcm)
    }

    private fun discardFailed() {
        if (state != State.RETRY) return
        failedPcm = null
        reset("Recording discarded")
    }

    private fun startRecording(via: String, yiddish: Boolean = false) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            toast("Microphone permission is off. Open Word for Word to turn it on."); return
        }

        val bufSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        audioRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize
            )
        } catch (_: SecurityException) { toast("Microphone permission is off. Open Word for Word to turn it on."); return }

        // Fails when another app (a call, a voice recorder) is holding the microphone.
        val started = try {
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) false
            else { audioRecord!!.startRecording(); true }
        } catch (e: Exception) { false }
        if (!started) {
            audioRecord?.release(); audioRecord = null
            toast("Couldn't start the microphone. Another app may be using it.")
            return
        }
        pcmStream = ByteArrayOutputStream()
        finishEdit()
        typingYiddishId = null
        Analytics.track("recording_started", mapOf("via" to via))
        yiddishTake = yiddish
        state = State.RECORDING
        setBusy(false)
        setAppearance(if (yiddish) COLOR_YIDDISH else COLOR_RECORDING)
        if (yiddish) showFeedback("Yiddish", 1500)
        startPulse()
        buzz()

        thread {
            val buf = ByteArray(bufSize)
            while (state == State.RECORDING) {
                val n = audioRecord?.read(buf, 0, buf.size) ?: break
                if (n > 0) {
                    pcmStream?.write(buf, 0, n)
                    bars?.setLevel(levelOf(buf, n))
                }
            }
        }
    }

    private fun stopAndTranscribe() {
        state = State.TRANSCRIBING
        stopPulse()
        buzz()
        setAppearance(COLOR_BUSY)
        setBusy(true)

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        val pcm = pcmStream?.toByteArray() ?: ByteArray(0)
        pcmStream = null

        if (pcm.isEmpty()) { reset("No audio was recorded. Check that the microphone isn't in use by another app."); return }
        // 16 kHz, 16-bit mono = 32,000 bytes per second
        if (pcm.size < 32000 / 3) { reset("Recording too short. Tap, speak, then tap again."); return }

        // This fork is cloud-only; on-phone models aren't shipped.
        if (yiddishTake) transcribeYiddish(pcm) else transcribeApi(pcm)
    }

    /** A Yiddish take: the Yiddish model on Isaac's PC (see [YiddishServer]). */
    private fun transcribeYiddish(pcm: ByteArray) {
        val seconds = Math.round(pcm.size / 3200.0) / 10.0
        YiddishServer.transcribe(WavWriter.encode(pcm)) { result, id ->
            if (!result.text.isNullOrBlank()) {
                Analytics.track("transcribed", mapOf("model" to "yiddish-pc", "seconds" to seconds, "backup_model" to false, "free_minutes" to false))
                typingYiddishId = id
                handleTranscriptionResult(result.text, "yiddish-pc")
            } else if (result.code == 503 || (result.code == null && result.error != null)) {
                // Isaac (2026-11-08): when the PC with the Yiddish model is off, use the normal model (Auto).
                yiddishTake = false
                handler.post { showFeedback("The Yiddish model is off. Used your normal model.", 3000) }
                transcribeApi(pcm)
            } else {
                failTranscription(pcm, "yiddish-pc", result)
            }
        }
    }

    private fun transcribeLocal(pcm: ByteArray, transcriber: LocalTranscriber) {
        thread {
            try {
                // Convert 16-bit PCM bytes to float samples
                val samples = FloatArray(pcm.size / 2)
                for (i in samples.indices) {
                    val lo = pcm[i * 2].toInt() and 0xFF
                    val hi = pcm[i * 2 + 1].toInt()
                    samples[i] = ((hi shl 8) or lo).toShort().toFloat() / 32768f
                }

                val t0 = System.currentTimeMillis()
                val text = transcriber.transcribe(samples, SAMPLE_RATE)
                val ms = System.currentTimeMillis() - t0
                Log.i(TAG, "Local transcription: ${ms}ms, ${samples.size / SAMPLE_RATE}s audio")

                handleTranscriptionResult(text)
            } catch (e: Exception) {
                Log.e(TAG, "Local transcription failed", e)
                handler.post {
                    toast("Local error: ${e.message}")
                    state = State.IDLE
                    setBusy(false)
                    setAppearance(COLOR_IDLE)
                }
            }
        }
    }

    private fun transcribeApi(pcm: ByteArray) {
        val wav = WavWriter.encode(pcm)
        val apiKey = prefs().getString("api_key", "") ?: ""
        // The Play build with no key entered uses Word for Word's server and its free minutes.
        val deviceId = if (FreeMinutes.inUse(this)) FreeMinutes.deviceId(this) else null
        if (apiKey.isBlank() && deviceId == null) { failWithRetry(pcm, "Set your OpenRouter API key in the Word for Word app"); return }

        // With free minutes, Word for Word picks the model; the picker is hidden.
        val model = if (deviceId != null) TranscriberClient.DEFAULT_MODEL
                    else prefs().getString("cloud_model", TranscriberClient.DEFAULT_MODEL) ?: TranscriberClient.DEFAULT_MODEL
        val verbatim = prefs().getBoolean("verbatim", false)
        // With Yiddish picked, Yiddish takes go to the Yiddish model; a normal take lets the model guess.
        val language = (prefs().getString("language", "auto") ?: "auto").let { if (it == "yi") "auto" else it }
        // Audio length to a tenth of a second (16 kHz, 16-bit mono = 32,000 bytes a second), for usage stats.
        val seconds = Math.round(pcm.size / 3200.0) / 10.0
        TranscriberClient.transcribe(wav, apiKey, model, verbatim, language, deviceId) { result ->
            if (result.text != null && result.text.isNotBlank()) {
                Analytics.track("transcribed", mapOf("model" to model, "seconds" to seconds, "backup_model" to false, "free_minutes" to (deviceId != null)))
                handleTranscriptionResult(result.text, model)
            } else if (TranscriberClient.shouldTryAnotherModel(result.code, result.detail)) {
                // The chosen model is busy or down: send the same recording to another model right away.
                val backup = TranscriberClient.fallbackFor(model)
                Log.i(TAG, "$model failed (${result.code}), trying $backup")
                TranscriberClient.transcribe(wav, apiKey, backup, verbatim, language, deviceId) { second ->
                    Log.i(TAG, "$backup answered: ${if (!second.text.isNullOrBlank()) "text" else second.code?.let { "error $it" } ?: "no text"}")
                    if (second.text != null && second.text.isNotBlank()) {
                        Analytics.track("transcribed", mapOf("model" to backup, "seconds" to seconds, "backup_model" to true, "free_minutes" to (deviceId != null)))
                        showFeedback("${TranscriberClient.titleFor(model)} was unavailable. Used ${TranscriberClient.titleFor(backup)}.", 4000)
                        handleTranscriptionResult(second.text, backup)
                    } else {
                        failTranscription(pcm, model, if (second.error == null && second.text != null) second else result)
                    }
                }
            } else {
                failTranscription(pcm, model, result)
            }
        }
    }

    /**
     * When the model answered but heard no words, sending the same audio again gives the same
     * empty answer, so the button goes back to the mic and a tap starts a new recording.
     * Other failures (network, server) keep the recording for a retry.
     */
    private fun failTranscription(pcm: ByteArray, model: String, result: TranscriberClient.Result) {
        val heardNothing = result.error == null && result.text != null
        Analytics.track("transcription_failed", mapOf(
            "model" to model,
            "reason" to if (heardNothing) "no_words" else "error",
            "http_code" to (result.code ?: 0)))
        if (heardNothing) {
            handler.post { reset(NO_WORDS) }
        } else {
            failWithRetry(pcm, result.error ?: NO_WORDS)
        }
    }

    /** [usedModel] is the model that wrote [text], saved with it in history. */
    private fun handleTranscriptionResult(text: String?, usedModel: String? = null) {
        if (text.isNullOrBlank()) {
            handler.post {
                toast("No speech detected")
                state = State.IDLE
                setBusy(false)
                setAppearance(COLOR_IDLE)
            }
            return
        }

        // Save your exact words to history (kept 24 hours, or forever if that's turned on).
        try {
            val model = usedModel ?: if (prefs().getBoolean("use_local", false)) "local"
                        else prefs().getString("cloud_model", TranscriberClient.DEFAULT_MODEL) ?: ""
            HistoryStore.add(this, text, model)
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't save to history", e)
        }

        val usePostProcessing = prefs().getBoolean("use_post_processing", false)
        val apiKey = prefs().getString("api_key", "") ?: ""

        if (usePostProcessing) {
            if (apiKey.isBlank()) {
                handler.post {
                    toast("Post-processing needs API key. Using raw text.")
                    injectText(text)
                    state = State.IDLE
                    setBusy(false)
                    setAppearance(COLOR_IDLE)
                }
                return
            }

            val prompt = prefs().getString("post_processing_prompt", PostProcessor.DEFAULT_PROMPT) ?: PostProcessor.DEFAULT_PROMPT
            
            PostProcessor.process(text, prompt, apiKey) { result ->
                handler.post {
                    if (result.text != null && result.text.isNotBlank()) {
                        injectText(result.text)
                    } else {
                        injectText(text)
                        showFeedback("Cleanup failed. Inserted your exact words.", 3000)
                    }
                    state = State.IDLE
                    setBusy(false)
                    setAppearance(COLOR_IDLE)
                }
            }
        } else {
            handler.post {
                injectText(text)
                state = State.IDLE
                setBusy(false)
                setAppearance(COLOR_IDLE)
            }
        }
    }

    private fun reset(msg: String) {
        toast(msg)
        state = State.IDLE
        setBusy(false)
        setAppearance(COLOR_IDLE)
    }

    // --- Text injection ---

    /**
     * Types the text straight into the focused box without touching the clipboard.
     * Only when no box accepts typed text (for example, Termux's terminal) does it
     * fall back to copying to the clipboard and pasting, and it says so on screen.
     */
    private fun injectText(text: String) {
        val candidates = findInjectionCandidates()
        Log.i(TAG, "Injecting text into ${candidates.size} candidate node(s)")

        var typed = false
        try {
            // First pass: set the text directly. No clipboard involved.
            for (candidate in candidates) {
                if (trySetText(candidate, text)) { typed = true; break }
            }
            // Second pass, only for boxes that refuse direct typing: clipboard + paste.
            if (!typed) {
                val clip = ClipData.newPlainText("Word for Word", text)
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                var pasted = false
                for (candidate in candidates) {
                    if (tryPaste(candidate)) { pasted = true; break }
                }
                Analytics.track("text_inserted", mapOf("method" to if (pasted) "pasted" else "clipboard_only"))
                showFeedback(if (pasted) "Pasted (this box doesn't accept typed text)"
                             else "Couldn't type here. Copied to clipboard and saved to history", 3000)
                if (!pasted) {
                    showingPasteFailure = true
                    handler.removeCallbacks(endPasteFailure)
                    handler.postDelayed(endPasteFailure, 3000)
                }
            }
            if (typed) Analytics.track("text_inserted", mapOf("method" to "typed"))
        } finally {
            candidates.forEach { it.recycle() }
        }

        Log.i(TAG, if (typed) "Text typed with ACTION_SET_TEXT" else "Fell back to clipboard")
    }

    private fun findInjectionCandidates(): List<AccessibilityNodeInfo> {
        val candidates = mutableListOf<AccessibilityNodeInfo>()

        rootInActiveWindow?.let { root ->
            Log.i(TAG, "Active root: package=${root.packageName} class=${root.className}")
            collectInjectionCandidates(root, candidates)
            root.recycle()
        }

        windows
            ?.filter { it.isActive || it.isFocused }
            ?.forEach { window ->
                val root = window.root ?: return@forEach
                Log.i(
                    TAG,
                    "Window root: type=${window.type} active=${window.isActive} focused=${window.isFocused} package=${root.packageName} class=${root.className}"
                )
                collectInjectionCandidates(root, candidates)
                root.recycle()
            }

        return candidates.sortedByDescending(::candidateScore)
    }

    private fun collectInjectionCandidates(
        root: AccessibilityNodeInfo,
        out: MutableList<AccessibilityNodeInfo>
    ) {
        root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.let { out += it }
        root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)?.let { out += it }
        collectPotentialTargets(root, out)
    }

    private fun collectPotentialTargets(
        node: AccessibilityNodeInfo,
        out: MutableList<AccessibilityNodeInfo>
    ) {
        if (isPotentialInjectionTarget(node)) {
            out += AccessibilityNodeInfo.obtain(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                collectPotentialTargets(child, out)
            } finally {
                child.recycle()
            }
        }
    }

    private fun isPotentialInjectionTarget(node: AccessibilityNodeInfo): Boolean {
        val className = node.className?.toString().orEmpty()
        return node.isFocused ||
            node.isEditable ||
            className.contains("EditText") ||
            className.contains("TerminalView") ||
            findCustomPasteAction(node) != null
    }

    private fun candidateScore(node: AccessibilityNodeInfo): Int {
        val className = node.className?.toString().orEmpty()
        var score = 0
        if (findCustomPasteAction(node) != null) score += 100
        if (className.contains("TerminalView")) score += 80
        if (node.isEditable) score += 60
        if (node.isFocused) score += 40
        if (className.contains("EditText")) score += 20
        return score
    }

    /**
     * Inserts the text at the cursor (or over the selected text) by setting the box's
     * whole contents, then puts the cursor right after the new words.
     */
    private fun trySetText(node: AccessibilityNodeInfo, text: String): Boolean {
        logNode("Trying node", node)
        if (!node.isEditable && node.className?.toString()?.contains("EditText") != true) return false

        // Read the box fresh, not from Android's cache, so the placeholder flag is current.
        node.refresh()
        val current = if (isPlaceholder(node)) "" else node.text?.toString().orEmpty()
        val selStart = node.textSelectionStart
        val selEnd = node.textSelectionEnd
        // On Android 7 and older (and in some apps), an empty box reports its grey placeholder,
        // like "Type a message...", as its text, with no cursor or the cursor at the start.
        // So text with the cursor missing or at the start gets pasted at the box's real cursor
        // instead: the placeholder never gets typed, and real text, if that's what it was, stays
        // untouched with the words added where the cursor is. (A friend's build also treats a
        // cursor at the start as a placeholder sign.)
        if (current.isNotEmpty() && selStart <= 0 && selEnd <= 0) {
            val clip = ClipData.newPlainText("Word for Word", text)
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
            val ok = tryPaste(node)
            Log.i(TAG, "No cursor in a box with text; pasted => $ok")
            if (ok) return true
        }
        val start = if (selStart in 0..current.length) selStart else current.length
        val end = if (selEnd in 0..current.length) selEnd else start
        val from = minOf(start, end)
        val to = maxOf(start, end)

        // Add a space when dictating right after a word, so two dictations don't run together.
        val needsSpace = from > 0 && !current[from - 1].isWhitespace() && !text.first().isWhitespace()
        val insert = if (needsSpace) " $text" else text
        val updated = current.replaceRange(from, to, insert)

        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, updated)
        }
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        Log.i(TAG, "ACTION_SET_TEXT => $ok")
        if (!ok) return false

        val cursor = from + insert.length
        typingYiddishId?.let { id ->
            typingYiddishId = null
            finishEdit()
            pendingEdit = PendingEdit(id, current.substring(0, from), current.substring(to), text.trim())
            Log.i(TAG, "Watching edits of Yiddish take $id")
            handler.postDelayed(finishEditRunnable, 2 * 60 * 1000L)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor)
        })
        return true
    }

    /**
     * An empty box reports its grey placeholder ("Message" in WhatsApp) as its text.
     * Some apps flag this correctly and some only sometimes, so this checks three ways:
     * the flag, the box's declared placeholder, and placeholders this box showed before.
     */
    private fun isPlaceholder(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return true
        val key = placeholderKey(node)
        // Android 7 and older don't report placeholders, so only remembered ones are caught there.
        if (android.os.Build.VERSION.SDK_INT < 26) return key != null && knownPlaceholders().contains("$key|$text")
        if (node.isShowingHintText) {
            rememberPlaceholder(key, text)
            return true
        }
        val hint = node.hintText?.toString()?.trim()
        if (!hint.isNullOrEmpty() && text == hint) {
            rememberPlaceholder(key, text)
            return true
        }
        return key != null && knownPlaceholders().contains("$key|$text")
    }

    /** Identifies a text box by app and view ID, for example WhatsApp's message box. */
    private fun placeholderKey(node: AccessibilityNodeInfo): String? {
        val id = node.viewIdResourceName ?: return null
        return "${node.packageName}/$id"
    }

    private fun knownPlaceholders(): Set<String> =
        BUILT_IN_PLACEHOLDERS + (prefs().getStringSet("known_placeholders", emptySet()) ?: emptySet())

    private fun rememberPlaceholder(key: String?, text: String) {
        if (key == null) return
        val entry = "$key|$text"
        val known = knownPlaceholders()
        if (entry in known) return
        prefs().edit().putStringSet("known_placeholders", known + entry).apply()
    }

    /** Fallback for boxes that won't take typed text: paste whatever is on the clipboard. */
    private fun tryPaste(node: AccessibilityNodeInfo): Boolean {
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        findCustomPasteAction(node)?.let { action ->
            val ok = node.performAction(action.id)
            Log.i(TAG, "Custom action '${action.label}' (${action.id}) => $ok")
            if (ok) return true
        }
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        Log.i(TAG, "ACTION_PASTE => $ok")
        return ok
    }

    private fun findCustomPasteAction(node: AccessibilityNodeInfo): AccessibilityNodeInfo.AccessibilityAction? =
        node.actionList.firstOrNull { action ->
            action.label?.toString()?.contains("paste", ignoreCase = true) == true
        }

    private fun logNode(prefix: String, node: AccessibilityNodeInfo) {
        val actions = node.actionList.joinToString { action ->
            action.label?.toString() ?: action.id.toString()
        }
        Log.i(
            TAG,
            "$prefix package=${node.packageName} class=${node.className} focused=${node.isFocused} editable=${node.isEditable} text=${node.text} desc=${node.contentDescription} actions=[$actions]"
        )
    }

    private fun prefs() = getSharedPreferences("phonewhisper", MODE_PRIVATE)
    /**
     * Shows a message next to the floating button. (Android hides Toast messages from
     * background services on recent versions, which made errors silent, so messages use
     * the app's own on-screen bubble instead.)
     */
    private fun toast(msg: String) {
        Log.w(TAG, "Message to user: $msg")
        showFeedback(msg, 4500)
    }
}
