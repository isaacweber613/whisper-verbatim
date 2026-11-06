package com.kafkasl.phonewhisper

import android.app.Application
import android.content.Context
import com.posthog.PostHog
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig

/**
 * Anonymous usage counts sent to PostHog, like T3 Code does: the app opening, recordings
 * starting, transcriptions working or failing, and how the text got in. Never audio, never
 * the words, never the API key. A random ID per install, no account. "Share usage data" in
 * Settings (pref "analytics", on by default) turns it off.
 */
object Analytics {
    private const val PREF = "analytics"
    private var ready = false

    fun init(app: Application) {
        if (BuildConfig.POSTHOG_KEY.isEmpty() || ready) return
        val config = PostHogAndroidConfig(apiKey = BuildConfig.POSTHOG_KEY, host = BuildConfig.POSTHOG_HOST).apply {
            optOut = !enabled(app)
            sessionReplay = false
        }
        PostHogAndroid.setup(app, config)
        // Which of the three builds (browser, selfInstall, play) and whether it's a test build.
        PostHog.register("flavor", BuildConfig.FLAVOR)
        PostHog.register("debug_build", BuildConfig.DEBUG)
        ready = true
    }

    fun enabled(context: Context) = prefs(context).getBoolean(PREF, true)

    fun setEnabled(context: Context, on: Boolean) {
        // Record the switch-off itself, so the counts show how many people turn it off.
        if (!on) track("analytics_turned_off")
        prefs(context).edit().putBoolean(PREF, on).apply()
        if (!ready) return
        if (on) PostHog.optIn() else PostHog.optOut()
    }

    fun track(event: String, properties: Map<String, Any> = emptyMap()) {
        if (ready) PostHog.capture(event = event, properties = properties)
    }

    private fun prefs(context: Context) = context.getSharedPreferences("phonewhisper", Context.MODE_PRIVATE)
}

/** Starts analytics once per process, for both the settings screen and the dictation service. */
class WordForWordApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Analytics.init(this)
    }
}
