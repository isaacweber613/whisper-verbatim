package com.kafkasl.phonewhisper

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.provider.Settings
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Tapping Update in the selfInstall build: the first tap downloads the APK inside the app,
 * the next tap installs it with Android's PackageInstaller. Android still shows its own
 * confirmation screen. Needs REQUEST_INSTALL_PACKAGES (src/selfInstall/AndroidManifest.xml),
 * which makes Google Play Protect block the app as harmful.
 */
class UpdateAction(private val activity: Activity, private val subtitle: TextView) {
    private val client = OkHttpClient()
    private var apk: File? = null
    private var downloading = false

    fun onTap(release: Updater.Release) {
        if (downloading) return
        apk?.let { install(it); return }
        downloading = true
        subtitle.text = "Downloading..."
        Thread {
            try {
                val file = download(release) { pct -> activity.runOnUiThread { subtitle.text = "Downloading $pct%" } }
                activity.runOnUiThread {
                    apk = file
                    downloading = false
                    subtitle.text = "Tap to install"
                }
            } catch (e: Exception) {
                activity.runOnUiThread {
                    downloading = false
                    subtitle.text = "Download failed. Tap to try again"
                }
            }
        }.start()
    }

    /** Downloads the APK into the app's cache, reporting 0-100 to [progress]. Call off the main thread. */
    private fun download(release: Updater.Release, progress: (Int) -> Unit): File {
        val file = File(activity.cacheDir, "update.apk")
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

    private fun install(apk: File) {
        // Android asks once whether this app may install apps.
        // Before Android 8, the phone-wide "Unknown sources" switch covers this, and it's on already.
        if (android.os.Build.VERSION.SDK_INT >= 26 && !activity.packageManager.canRequestPackageInstalls()) {
            activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                android.net.Uri.parse("package:${activity.packageName}")))
            Toast.makeText(activity, "Allow Word for Word to install updates, then come back and tap Install", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val installer = activity.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                .apply { setAppPackageName(activity.packageName) }
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite(Updater.APK_NAME, 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val status = Intent(activity, InstallStatusReceiver::class.java).setAction(ACTION_INSTALL_STATUS)
                // Mutable: Android fills in the install status.
                val pending = PendingIntent.getBroadcast(activity, sessionId, status,
                    PendingIntent.FLAG_UPDATE_CURRENT or (if (android.os.Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0))
                session.commit(pending.intentSender)
            }
        } catch (e: Exception) {
            Toast.makeText(activity, "The update didn't install. Try again.", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "com.isaacweber.wordforword.INSTALL_STATUS"
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
