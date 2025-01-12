package com.mrannonymus.discordwithnormalnotificationsunifiedpush

import android.content.Context

class PreferencesManager private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("discord_prefs", Context.MODE_PRIVATE)

    var isConfigured: Boolean
        get() = prefs.getBoolean(KEY_IS_CONFIGURED, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_CONFIGURED, value).apply()

    companion object {
        private const val KEY_IS_CONFIGURED = "is_configured"
        
        @Volatile
        private var instance: PreferencesManager? = null

        fun getInstance(context: Context): PreferencesManager {
            return instance ?: synchronized(this) {
                instance ?: PreferencesManager(context).also { instance = it }
            }
        }
    }
} 