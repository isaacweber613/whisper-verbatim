package com.kafkasl.phonewhisper

import android.content.SharedPreferences
import android.view.KeyEvent

/**
 * The keyboard shortcut that starts and stops talking, for phones with a physical keypad.
 * Saved as prefs "shortcut_enabled", "shortcut_keys" (key codes, comma separated) and
 * "shortcut_hold_ms" (0 = a quick press) and "shortcut_block_key" (a one-key shortcut
 * stops doing its normal job; two-key shortcuts never block their keys).
 */
object Shortcut {
    const val DEFAULT_HOLD_MS = 0L

    fun enabled(p: SharedPreferences) = p.getBoolean("shortcut_enabled", false) && keys(p).isNotEmpty()

    fun keys(p: SharedPreferences): Set<Int> =
        (p.getString("shortcut_keys", "") ?: "").split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()

    fun holdMs(p: SharedPreferences) = p.getLong("shortcut_hold_ms", DEFAULT_HOLD_MS)

    /** The second shortcut, for Yiddish takes ("shortcut_yiddish_keys"); used only with Yiddish picked. */
    fun yiddishKeys(p: SharedPreferences): Set<Int> =
        (p.getString("shortcut_yiddish_keys", "") ?: "").split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()

    /** Only a one-key shortcut can block its key. */
    fun blocksKey(p: SharedPreferences) = p.getBoolean("shortcut_block_key", false) && keys(p).size == 1

    /** Kept in the order you pressed them, so "* + 5" reads the way you press it. */
    fun encode(keys: Set<Int>) = keys.joinToString(",")

    /** "5", "Call + 5", "*". */
    fun describe(keys: Set<Int>): String = keys.joinToString(" + ") { keyName(it) }

    fun keyName(code: Int): String = when (code) {
        KeyEvent.KEYCODE_STAR -> "*"
        KeyEvent.KEYCODE_POUND -> "#"
        KeyEvent.KEYCODE_DPAD_CENTER -> "OK"
        KeyEvent.KEYCODE_DPAD_UP -> "Up"
        KeyEvent.KEYCODE_DPAD_DOWN -> "Down"
        KeyEvent.KEYCODE_DPAD_LEFT -> "Left"
        KeyEvent.KEYCODE_DPAD_RIGHT -> "Right"
        else -> {
            val raw = KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
            if (raw.toIntOrNull() != null && raw.length < 3) raw   // digit keys
            else raw.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
        }
    }

    fun describeHold(ms: Long): String = when (ms) {
        0L -> "Quick press"
        500L -> "Hold half a second"
        1000L -> "Hold 1 second"
        else -> "Hold ${formatSeconds(ms)} seconds"
    }

    fun formatSeconds(ms: Long): String {
        val s = ms / 1000.0
        return if (s == Math.floor(s)) s.toLong().toString() else s.toString()
    }
}

/**
 * Watches key presses for the shortcut. It fires when exactly the shortcut keys are down;
 * with a hold time, [holdElapsed] is called after that time and fires only if they are still
 * held. Keys reach the app as usual, unless [blockKey] is set for a one-key shortcut: then
 * [blocks] hides that key from apps.
 */
class ShortcutMatcher(private val keys: Set<Int>, private val holdMs: Long, private val blockKey: Boolean = false) {
    companion object {
        /**
         * Some flip phones (seen on a friend's Kyocera, 2026-11-06) report one press of a key
         * twice. That started recording and stopped it right away, ending in "No speech
         * detected". A shortcut key pressed again within this time after letting go is ignored.
         */
        const val BOUNCE_MS = 300L
        /** A second start or stop within this time of the last one is ignored. */
        const val REFIRE_MS = 1000L
    }

    /** True when this key's press, repeat or release should be hidden from apps. */
    fun blocks(code: Int) = blockKey && keys.size == 1 && code in keys

    enum class Result { NONE, FIRE, WAIT }

    private val down = mutableSetOf<Int>()
    private val lastUp = mutableMapOf<Int, Long>()
    private var lastFire: Long? = null
    private var armed = false
    private var fired = false

    /** [time] is the key event's time in milliseconds (KeyEvent.eventTime). */
    fun onDown(code: Int, repeatCount: Int, time: Long): Result {
        if (repeatCount > 0 || code in down) return Result.NONE
        if (code in keys) {
            val up = lastUp[code]
            if (up != null && time - up < BOUNCE_MS) return Result.NONE
        }
        down += code
        if (code !in keys || down != keys) return Result.NONE
        armed = true
        if (holdMs > 0) {
            fired = false
            return Result.WAIT
        }
        fired = true
        return if (allowFire(time)) Result.FIRE else Result.NONE
    }

    /** True once, when the hold time has passed and the shortcut keys are still held. */
    fun holdElapsed(time: Long): Boolean {
        if (!armed || fired || down != keys) return false
        fired = true
        return allowFire(time)
    }

    fun onUp(code: Int, time: Long) {
        down -= code
        if (code in keys) {
            armed = false
            lastUp[code] = time
        }
    }

    private fun allowFire(time: Long): Boolean {
        val last = lastFire
        if (last != null && time - last < REFIRE_MS) return false
        lastFire = time
        return true
    }
}
