package to.us.charlesst.discord

import android.content.Context
import android.util.Log
import org.unifiedpush.android.connector.MessagingReceiver
import org.unifiedpush.android.connector.UnifiedPush

private const val TAG = "PushServiceImpl"

class PushServiceImpl : MessagingReceiver() {
    companion object {
        private var helper: UnifiedPushHelper? = null
    }

    override fun onNewEndpoint(context: Context, endpoint: String, instance: String) {
        Log.d(TAG, "New Endpoint: $endpoint instance: $instance")
        
        // Make sure we only process valid endpoints
        if (endpoint.isBlank() || endpoint == "http://localhost") {
            Log.d(TAG, "Ignoring invalid endpoint: $endpoint")
            return
        }
        
        getHelper(context).onNewEndpoint(endpoint)
    }

    override fun onMessage(context: Context, message: ByteArray, instance: String) {
        Log.d(TAG, "Received message instance: $instance")
        getHelper(context).handleMessage(message)
    }

    override fun onRegistrationFailed(context: Context, instance: String) {
        Log.e(TAG, "Registration Failed for instance: $instance")
        val helper = getHelper(context)
        
        // Try once more with a different distributor if available
        val distributors = UnifiedPush.getDistributors(context)
        if (distributors.size > 1) {
            val currentDistributor = UnifiedPush.getDistributor(context)
            val otherDistributors = distributors.filter { it != currentDistributor }
            
            if (otherDistributors.isNotEmpty()) {
                val newDistributor = otherDistributors[0]
                Log.d(TAG, "Trying registration with alternative distributor: $newDistributor")
                
                UnifiedPush.saveDistributor(context, newDistributor)
                UnifiedPush.registerApp(context)
                return
            }
        }
        
        helper.onRegistrationFailed()
    }

    override fun onUnregistered(context: Context, instance: String) {
        Log.d(TAG, "Unregistered instance: $instance")
        getHelper(context).onUnregistered()
    }
    
    private fun getHelper(context: Context): UnifiedPushHelper {
        return helper ?: UnifiedPushHelper.getInstance(context).also { helper = it }
    }
}