package com.kafkasl.phonewhisper

import android.app.Activity
import android.widget.TextView

/** The Play build never offers an update itself (BuildConfig.UPDATES is false); Play installs updates. */
class UpdateAction(@Suppress("unused") private val activity: Activity, @Suppress("unused") private val subtitle: TextView) {
    fun onTap(@Suppress("UNUSED_PARAMETER") release: Updater.Release) = Unit
}
