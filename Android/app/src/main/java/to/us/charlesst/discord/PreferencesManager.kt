package to.us.charlesst.discord

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isFirstLaunch(): Boolean {
        return prefs.getBoolean(KEY_FIRST_LAUNCH, true)
    }

    fun setFirstLaunchComplete() {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
    }
    
    fun getNotificationStyle(): Int {
        return prefs.getInt(KEY_NOTIFICATION_STYLE, NOTIFICATION_STYLE_SINGLE)
    }
    
    fun setNotificationStyle(style: Int) {
        prefs.edit()
            .putInt(KEY_NOTIFICATION_STYLE, style)
            .putBoolean(KEY_NOTIFICATION_STYLE_SET, true)
            .apply()
    }
    
    fun isNotificationStyleSet(): Boolean {
        return prefs.getBoolean(KEY_NOTIFICATION_STYLE_SET, false)
    }

    companion object {
        private const val PREFS_NAME = "DiscordPrefs"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_NOTIFICATION_STYLE = "notification_style"
        private const val KEY_NOTIFICATION_STYLE_SET = "notification_style_set"
        
        // Notification style constants
        const val NOTIFICATION_STYLE_SINGLE = 0 // Current approach - single notification with timestamp update
        const val NOTIFICATION_STYLE_MULTI = 1 // Multiple notifications with content
        const val NOTIFICATION_STYLE_HYBRID = 2 // Single notification with content updates
    }
}
