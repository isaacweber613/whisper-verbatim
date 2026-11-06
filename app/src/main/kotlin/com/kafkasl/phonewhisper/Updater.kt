package com.kafkasl.phonewhisper

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Update check for the sideloaded APK: looks at the latest GitHub release for this edition's
 * APK. What tapping Update does lives in [UpdateAction]: the selfInstall editions download
 * and install it in the app; the browser editions open it in the browser, because an
 * accessibility app that can install other apps gets blocked by Google Play Protect.
 */
object Updater {
    private const val TAG = "PhoneWhisper"
    private const val LATEST_RELEASE = "https://api.github.com/repos/isaacweber613/whisper-verbatim/releases/latest"
    /** Each edition updates to its own file in the release. */
    const val APK_NAME = BuildConfig.APK_BASE + BuildConfig.APK_SUFFIX + ".apk"

    data class Release(val version: String, val apkUrl: String, val size: Long)

    private val client = OkHttpClient()

    /** The latest release if it's newer than [current], else null. Runs network I/O: call off the main thread. */
    fun checkForUpdate(current: String): Release? = try {
        val request = Request.Builder().url(LATEST_RELEASE)
            .header("Accept", "application/vnd.github+json").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val release = parseRelease(response.body?.string() ?: return null)
            release?.takeIf { isNewer(it.version, current) }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Update check failed: $e")
        null
    }

    fun parseRelease(json: String, apkName: String = APK_NAME): Release? {
        val obj = JSONObject(json)
        val version = obj.optString("tag_name").removePrefix("v").ifBlank { return null }
        val assets = obj.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (asset.optString("name") == apkName) {
                return Release(version, asset.getString("browser_download_url"), asset.optLong("size"))
            }
        }
        return null
    }

    /** Compares dotted version numbers, so 0.6.10 is newer than 0.6.9. */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = candidate.split('.').map { it.toIntOrNull() ?: 0 }
        val b = current.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
