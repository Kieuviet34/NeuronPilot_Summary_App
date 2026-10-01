package com.mediatek.neuropilot.jnidemo.chat

import android.content.Context

object AppSettings {
    private const val PREFS_NAME = "app_settings"
    private const val KEY_MAX_TURNS = "max_turns"
    const val DEFAULT_MAX_TURNS = 100

    fun getMaxTurns(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return (prefs.getInt(KEY_MAX_TURNS, DEFAULT_MAX_TURNS)).coerceAtLeast(1)
    }

    fun setMaxTurns(context: Context, maxTurns: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_MAX_TURNS, maxTurns.coerceAtLeast(1)).apply()
    }
}
