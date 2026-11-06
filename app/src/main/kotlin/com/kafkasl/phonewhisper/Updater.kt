package com.kafkasl.phonewhisper

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import android.widget.Toast
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

/**
 * In-app updates for the sideloaded APK: checks the latest GitHub release, downloads its
 * APK inside the app, and installs it with Android's PackageInstaller. Android still shows
 * its own confirmation screen; the person never leaves the app to find the file.
 */
object Updater {
    private const val TAG = "PhoneWhisper"
    private const val LATEST_RELEASE = "https://api.github.com/repos/isaacweber613/whisper-verbatim/releases/latest"
    private const val APK_NAME = "WordForWord.apk"
    const val ACTION_INSTALL_STATUS = "com.isaacweber.wordforword.INSTALL_STATUS"

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

    fun parseRelease(json: String): Release? {
        val obj = JSONObject(json)
        val version = obj.optString("tag_name").removePrefix("v").ifBlank { return null }
        val assets = obj.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (asset.optString("name") == APK_NAME) {
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

    /** Downloads the APK into the app's cache, reporting 0-100 to [progress]. Call off the main thread. */
    fun download(context: Context, release: Release, progress: (Int) -> Unit): File {
        val file = File(context.cacheDir, "update.apk")
        client.newCall(Request.Builder().url(release.apkUrl).build()).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Download failed (error ${response.code})")
            val body = response.body ?: throw IllegalStateException("Download failed")
            val total = body.contentLength().takeIf { it > 0 } ?: release.size
            body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) progress((done * 100 / total).toInt())
                    }
                }
            }
        }
        return file
    }

    /** Hands the downloaded APK to Android's installer. [InstallStatusReceiver] gets the result. */
    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply { setAppPackageName(context.packageName) }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite(APK_NAME, 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val status = Intent(context, InstallStatusReceiver::class.java).setAction(ACTION_INSTALL_STATUS)
            // Mutable: Android fills in the install status.
            val pending = PendingIntent.getBroadcast(context, sessionId, status,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            session.commit(pending.intentSender)
        }
    }
}

/** Shows Android's install confirmation, or says why the update didn't install. */
class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_FAILURE_ABORTED -> Unit // The person tapped Cancel.
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Log.w("PhoneWhisper", "Update install failed: $msg")
                Toast.makeText(context, "The update didn't install. Try again from Word for Word.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
