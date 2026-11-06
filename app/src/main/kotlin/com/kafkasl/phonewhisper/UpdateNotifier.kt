package com.kafkasl.phonewhisper

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlin.concurrent.thread

/**
 * Optional "Update notifications" (pref "update_notifications", off by default): once a day
 * the phone checks GitHub for a newer release and shows a notification, once per version.
 * Android's daily alarm is cleared on restart, so the accessibility service, which starts with
 * the phone, schedules it again ([schedule] is safe to call any number of times).
 */
object UpdateNotifier {
    private const val CHANNEL = "updates"
    private const val NOTIFICATION_ID = 1
    private const val PREF = "update_notifications"
    private const val PREF_LAST = "update_notified_version"

    /** Always off in the Play build, which gets its updates from Play. */
    fun enabled(context: Context) = BuildConfig.UPDATES && prefs(context).getBoolean(PREF, false)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(PREF, on).apply()
        schedule(context)
    }

    /** Starts or stops the daily check to match the setting. */
    fun schedule(context: Context) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = alarmIntent(context)
        alarms.cancel(pending)
        if (!enabled(context)) return
        // Inexact, so Android can batch it with other apps' alarms and save battery.
        alarms.setInexactRepeating(AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_DAY, AlarmManager.INTERVAL_DAY, pending)
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, UpdateCheckReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0))

    /** Checks GitHub and notifies about a newer version it hasn't notified about yet. Network: call off the main thread. */
    fun checkAndNotify(context: Context) {
        if (!enabled(context)) return
        val current = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: return
        val release = Updater.checkForUpdate(current) ?: return
        if (prefs(context).getString(PREF_LAST, null) == release.version) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return

        val manager = NotificationManagerCompat.from(context)
        if (Build.VERSION.SDK_INT >= 26) {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
                NotificationChannel(CHANNEL, "Updates", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = PendingIntent.getActivity(context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0))
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle("Word for Word ${release.version} is out")
            .setContentText("Tap to open the app and update")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
            prefs(context).edit().putString(PREF_LAST, release.version).apply()
        } catch (_: SecurityException) {
            // Notifications are turned off for the app.
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences("phonewhisper", Context.MODE_PRIVATE)
}

/** Runs the daily update check. */
class UpdateCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        thread {
            try { UpdateNotifier.checkAndNotify(context.applicationContext) } finally { result.finish() }
        }
    }
}
