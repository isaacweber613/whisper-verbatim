package com.kafkasl.phonewhisper

import android.app.Activity
import android.content.Intent
import android.widget.TextView
import android.widget.Toast

/**
 * Tapping Update in the browser editions: opens the new APK in the browser, and Android's own
 * installer takes it from there. These editions have no permission to install apps, so
 * Google Play Protect doesn't block them.
 */
class UpdateAction(private val activity: Activity, @Suppress("unused") private val subtitle: TextView) {
    fun onTap(release: Updater.Release) {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(release.apkUrl)))
        } catch (e: Exception) {
            Toast.makeText(activity, "Couldn't open the browser to download the update.", Toast.LENGTH_LONG).show()
        }
    }
}
