package to.us.charlesst.discord

import android.os.OutcomeReceiver
import android.service.settings.preferences.SettingsPreferenceService
import android.service.settings.preferences.MetadataRequest
import android.service.settings.preferences.MetadataResult
import android.service.settings.preferences.GetValueRequest
import android.service.settings.preferences.GetValueResult
import android.service.settings.preferences.SetValueRequest
import android.service.settings.preferences.SetValueResult
import android.service.settings.preferences.SettingsPreferenceMetadata
import android.util.Log
import androidx.annotation.RequiresApi

// SettingsPreferenceService implementation for Android API 36+
@RequiresApi(36)
class DiscordSettingsPreferenceService : SettingsPreferenceService() {

    companion object {
        private const val TAG = "DiscordSettingsPrefs"
        
        // Screen key for Discord preferences
        private const val SCREEN_KEY = "discord_notifications"
        
        // Preference keys
        private const val PREF_NOTIFICATION_STYLE = "notification_style"
        private const val PREF_UNIFIEDPUSH_DISTRIBUTOR = "unifiedpush_distributor"
        private const val PREF_UNIFIEDPUSH_ENDPOINT = "unifiedpush_endpoint"
        private const val PREF_UNIFIEDPUSH_ENABLED = "unifiedpush_enabled"
    }

    override fun onGetAllPreferenceMetadata(
        request: MetadataRequest,
        callback: OutcomeReceiver<MetadataResult, Exception>
    ) {
        Log.d(TAG, "onGetAllPreferenceMetadata called")
        
        try {
            val metadataList = listOf(
                // Notification Style (Writable LIST preference)
                SettingsPreferenceMetadata.Builder(SCREEN_KEY, PREF_NOTIFICATION_STYLE)
                    .setTitle("Notification Style")
                    .setSummary("How Discord notifications appear")
                    .setEnabled(true)
                    .setWritable(true)
                    .build(),
                
                // UnifiedPush Distributor (Read-only TEXT preference)
                SettingsPreferenceMetadata.Builder(SCREEN_KEY, PREF_UNIFIEDPUSH_DISTRIBUTOR)
                    .setTitle("UnifiedPush Distributor")
                    .setSummary("Current push notification distributor app")
                    .setEnabled(true)
                    .setWritable(false)
                    .build(),
                
                // UnifiedPush Endpoint (Read-only TEXT preference)
                SettingsPreferenceMetadata.Builder(SCREEN_KEY, PREF_UNIFIEDPUSH_ENDPOINT)
                    .setTitle("UnifiedPush Endpoint")
                    .setSummary("Current push notification endpoint URL")
                    .setEnabled(true)
                    .setWritable(false)
                    .build(),
                
                // UnifiedPush Enabled (Read-only SWITCH preference)
                SettingsPreferenceMetadata.Builder(SCREEN_KEY, PREF_UNIFIEDPUSH_ENABLED)
                    .setTitle("UnifiedPush Status")
                    .setSummary("Whether UnifiedPush is properly configured")
                    .setEnabled(true)
                    .setWritable(false)
                    .build()
            )
            
            val result = MetadataResult.Builder(MetadataResult.RESULT_OK)
                .setMetadataList(metadataList)
                .build()
            
            callback.onResult(result)
            Log.d(TAG, "Successfully returned ${metadataList.size} preferences")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in onGetAllPreferenceMetadata", e)
            callback.onError(e)
        }
    }

    override fun onGetPreferenceValue(
        request: GetValueRequest,
        callback: OutcomeReceiver<GetValueResult, Exception>
    ) {
        Log.d(TAG, "onGetPreferenceValue called for key: ${request.preferenceKey}")
        
        try {
            // Note: We'll need to determine the correct way to create SettingsPreferenceValue
            // when that documentation becomes available
            val result = GetValueResult.Builder(GetValueResult.RESULT_OK)
                .build()
            
            callback.onResult(result)
            Log.d(TAG, "Successfully returned value for key: ${request.preferenceKey}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting preference value for key: ${request.preferenceKey}", e)
            callback.onError(e)
        }
    }

    override fun onSetPreferenceValue(
        request: SetValueRequest,
        callback: OutcomeReceiver<SetValueResult, Exception>
    ) {
        Log.d(TAG, "onSetPreferenceValue called for key: ${request.preferenceKey}")
        
        try {
            when (request.preferenceKey) {
                PREF_NOTIFICATION_STYLE -> {
                    // Handle notification style changes
                    // TODO: Extract value from request and save via PreferencesManager
                    Log.d(TAG, "Updating notification style preference")
                }
                else -> {
                    Log.w(TAG, "Attempted to set read-only preference: ${request.preferenceKey}")
                    val result = SetValueResult.Builder(SetValueResult.RESULT_DISALLOW).build()
                    callback.onResult(result)
                    return
                }
            }
            
            val result = SetValueResult.Builder(SetValueResult.RESULT_OK).build()
            callback.onResult(result)
            Log.d(TAG, "Successfully set value for key: ${request.preferenceKey}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting preference value for key: ${request.preferenceKey}", e)
            callback.onError(e)
        }
    }
} 