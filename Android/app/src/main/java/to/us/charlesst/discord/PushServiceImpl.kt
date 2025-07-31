package to.us.charlesst.discord

import android.content.Context
import android.util.Log
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.UnifiedPush
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

private const val TAG = "PushServiceImpl"

class PushServiceImpl : PushService() {
    companion object {
        private var helper: UnifiedPushHelper? = null
    }

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        Log.d(TAG, "New Endpoint: ${endpoint.url} instance: $instance")
        
        // Make sure we only process valid endpoints
        if (endpoint.url.isBlank() || endpoint.url == "http://127.0.0.1/") {
            Log.e(TAG, "Received invalid endpoint: ${endpoint.url}")
            return
        }
        
        try {
            // Save the current distributor that worked
            val currentDistributor = UnifiedPush.getSavedDistributor(this) ?: ""
            Log.d(TAG, "Registration successful with distributor: $currentDistributor")
            
            // Store web push encryption keys if provided
            endpoint.pubKeySet?.let { pubKeySet ->
                Log.d(TAG, "Storing web push encryption keys")
                val helper = getHelper(this)
                helper.preferencesManager.setWebPushPublicKey(pubKeySet.pubKey)
                helper.preferencesManager.setWebPushAuthSecret(pubKeySet.auth)
                Log.d(TAG, "Web push keys stored successfully")
            }
            
            // Get helper instance and handle the new endpoint
            getHelper(this).onNewEndpoint(endpoint.url)
            Log.d(TAG, "Successfully processed new endpoint")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing new endpoint", e)
        }
    }

    override fun onMessage(message: PushMessage, instance: String) {
        Log.d(TAG, "Received message, instance: $instance")
        
        try {
            getHelper(this).handleMessage(message.content)
            Log.d(TAG, "Successfully processed message")
        } catch (e: Exception) {
            Log.e(TAG, "Error processing message", e)
        }
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        Log.e(TAG, "Registration failed: $reason, instance: $instance")
        
        when (reason) {
            FailedReason.VAPID_REQUIRED -> {
                Log.d(TAG, "VAPID required by distributor")
                
                try {
                    val helper = getHelper(this)
                    helper.preferencesManager.setDistributorRequiresVapid(true)
                    
                    if (!helper.preferencesManager.getVapidEnabled()) {
                        Log.w(TAG, "VAPID required by distributor but disabled by user preference")
                        Log.w(TAG, "Please enable VAPID in settings or choose a different distributor")
                        UnifiedPush.removeDistributor(this)
                        helper.onRegistrationFailed()
                        return
                    }
                    
                    // Force regenerate VAPID keys in case the current ones are invalid
                    val vapidKey = helper.generateVapidKeys()
                    
                    if (vapidKey != null) {
                        Log.d(TAG, "Re-registering with fresh VAPID keys: $vapidKey")
                        UnifiedPush.register(this, instance, vapid = vapidKey)
                    } else {
                        Log.e(TAG, "Failed to regenerate VAPID keys")
                        UnifiedPush.removeDistributor(this)
                        helper.onRegistrationFailed()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling VAPID requirement", e)
                    UnifiedPush.removeDistributor(this)
                    getHelper(this).onRegistrationFailed()
                }
            }
            else -> {
                Log.d(TAG, "Registration failed for other reason: $reason")
                UnifiedPush.removeDistributor(this)
                
                try {
                    getHelper(this).onRegistrationFailed()
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling registration failure", e)
                }
            }
        }
    }

    override fun onUnregistered(instance: String) {
        Log.d(TAG, "Unregistered, instance: $instance")
        
        try {
            // Clear the endpoint when unregistered
            getHelper(this).onUnregistered()
            Log.d(TAG, "Successfully handled unregistration")
        } catch (e: Exception) {
            Log.e(TAG, "Error handling unregistration", e)
        }
    }

    private fun getHelper(context: Context): UnifiedPushHelper {
        return helper ?: UnifiedPushHelper.getInstance(context).also { helper = it }
    }
}