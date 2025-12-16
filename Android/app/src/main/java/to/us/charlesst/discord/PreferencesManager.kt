package to.us.charlesst.discord

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isFirstLaunch(): Boolean {
        return prefs.getBoolean(KEY_FIRST_LAUNCH, true)
    }

    fun setFirstLaunchComplete() {
        prefs.edit { putBoolean(KEY_FIRST_LAUNCH, false) }
    }
    
    fun getNotificationStyle(): Int {
        return prefs.getInt(KEY_NOTIFICATION_STYLE, NOTIFICATION_STYLE_MULTI)
    }
    
    fun setNotificationStyle(style: Int) {
        prefs.edit {
            putInt(KEY_NOTIFICATION_STYLE, style)
            putBoolean(KEY_NOTIFICATION_STYLE_SET, true)
        }
    }
    
    fun isNotificationStyleSet(): Boolean {
        return prefs.getBoolean(KEY_NOTIFICATION_STYLE_SET, false)
    }
    
    fun setCurrentDistributor(distributor: String) {
        prefs.edit { putString(KEY_CURRENT_DISTRIBUTOR, distributor) }
    }
    
    fun setCurrentEndpoint(endpoint: String) {
        prefs.edit { putString(KEY_CURRENT_ENDPOINT, endpoint) }
    }

    // VAPID Support
    fun getVapidPublicKey(): String? {
        return prefs.getString("vapid_public_key", null)
    }
    
    fun setVapidPublicKey(publicKey: String?) {
        prefs.edit { putString("vapid_public_key", publicKey) }
    }
    
    fun getDistributorRequiresVapid(): Boolean {
        return prefs.getBoolean("distributor_requires_vapid", false)
    }
    
    fun setDistributorRequiresVapid(required: Boolean) {
        prefs.edit { putBoolean("distributor_requires_vapid", required) }
    }
    
    fun getVapidEnabled(): Boolean {
        return prefs.getBoolean("vapid_enabled", false) // Default to false (opt-in)
    }
    
    // Web Push Encryption Keys
    fun getWebPushPublicKey(): String? {
        return prefs.getString("webpush_public_key", null)
    }
    
    fun setWebPushPublicKey(publicKey: String?) {
        prefs.edit { putString("webpush_public_key", publicKey) }
    }
    
    fun getWebPushAuthSecret(): String? {
        return prefs.getString("webpush_auth_secret", null)
    }
    
    fun setWebPushAuthSecret(authSecret: String?) {
        prefs.edit { putString("webpush_auth_secret", authSecret) }
    }

    companion object {
        private const val PREFS_NAME = "DiscordPrefs"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_NOTIFICATION_STYLE = "notification_style"
        private const val KEY_NOTIFICATION_STYLE_SET = "notification_style_set"
        private const val KEY_CURRENT_DISTRIBUTOR = "current_distributor"
        private const val KEY_CURRENT_ENDPOINT = "current_endpoint"

        // Notification style constants
        const val NOTIFICATION_STYLE_MULTI = 1 // Multiple notifications with content
        const val NOTIFICATION_STYLE_HYBRID = 2 // Single notification with content updates
    }
}
