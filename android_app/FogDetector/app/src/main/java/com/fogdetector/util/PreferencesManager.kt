package com.fogdetector.util

import android.content.Context
import androidx.core.content.edit

object PreferencesManager {

    private const val PREFS_NAME       = "fog_detector_prefs"
    private const val KEY_FOG_THRESHOLD = "fog_threshold"
    private const val KEY_AUTO_TACTILE  = "auto_tactile"
    private const val KEY_AUTO_NOTIFY   = "auto_notify"

    const val DEFAULT_FOG_THRESHOLD = 0.30f

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getFogThreshold(ctx: Context): Float =
        prefs(ctx).getFloat(KEY_FOG_THRESHOLD, DEFAULT_FOG_THRESHOLD)

    fun setFogThreshold(ctx: Context, v: Float) =
        prefs(ctx).edit { putFloat(KEY_FOG_THRESHOLD, v.coerceIn(0.05f, 0.95f)) }

    fun getAutoTactile(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_AUTO_TACTILE, true)

    fun setAutoTactile(ctx: Context, v: Boolean) =
        prefs(ctx).edit { putBoolean(KEY_AUTO_TACTILE, v) }

    fun getAutoNotify(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_AUTO_NOTIFY, true)

    fun setAutoNotify(ctx: Context, v: Boolean) =
        prefs(ctx).edit { putBoolean(KEY_AUTO_NOTIFY, v) }
}
