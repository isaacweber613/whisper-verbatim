package com.kafkasl.phonewhisper

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest

/**
 * Free minutes in the Play Store build (Isaac, 2026-11-06: 30 a month, Play build only).
 * With no OpenRouter key entered, recordings go to Word for Word's server (`server/` in this
 * repo), which holds an OpenRouter key and counts each phone's minutes. GitHub builds have no
 * server URL, so they always need the person's own key.
 */
object FreeMinutes {
    val available get() = BuildConfig.SERVICE_URL.isNotEmpty()

    /** True when recordings go to Word for Word's server: no key entered and this build has the server. */
    fun inUse(context: Context) = available && apiKey(context).isBlank()

    /**
     * Identifies the phone to the server without saying who it is: a hash of Android's ID for
     * this app, which stays the same if the app is reinstalled.
     */
    @SuppressLint("HardwareIds")
    fun deviceId(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        val digest = MessageDigest.getInstance("SHA-256").digest("word-for-word:$androidId".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    data class Usage(val usedSeconds: Double, val freeSeconds: Double)

    private val http by lazy { OkHttpClient() }

    /** Asks the server how much of this month's free time is used. Calls back with null on failure. */
    fun usage(context: Context, callback: (Usage?) -> Unit) {
        val request = Request.Builder().url(BuildConfig.SERVICE_URL + "/v1/usage")
            .header("X-Device-Id", deviceId(context)).get().build()
        http.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = callback(null)
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                callback(try {
                    if (!response.isSuccessful || body == null) null
                    else JSONObject(body).let { Usage(it.getDouble("used_seconds"), it.getDouble("free_seconds")) }
                } catch (e: Exception) { null })
            }
        })
    }

    private fun apiKey(context: Context) =
        context.getSharedPreferences("phonewhisper", Context.MODE_PRIVATE).getString("api_key", "") ?: ""
}
