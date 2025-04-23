package to.us.charlesst.discord

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.lifecycle.MutableLiveData
import org.unifiedpush.android.connector.UnifiedPush
import org.json.JSONObject
import java.lang.ref.WeakReference

private const val TAG = "UnifiedPush"

class UnifiedPushHelper private constructor(context: Context) {
    private val contextRef = WeakReference(context.applicationContext)
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    val endpoint = MutableLiveData<String?>(null)
    val distributors = MutableLiveData<List<String>>(emptyList())
    
    companion object {
        @Volatile
        private var instance: UnifiedPushHelper? = null
        private const val CHANNEL_ID = "discord_messages"
        private const val NOTIFICATION_ID = 1000 // Fixed ID to replace existing notifications
        
        @Synchronized
        fun getInstance(context: Context): UnifiedPushHelper {
            return instance ?: synchronized(this) {
                instance ?: UnifiedPushHelper(context.applicationContext).also { instance = it }
            }
        }
    }
    
    init {
        createNotificationChannel()
    }
    
    private fun getContext(): Context {
        return contextRef.get() ?: throw IllegalStateException("Context is no longer valid")
    }
    
    fun register() {
        val currentDistributors = UnifiedPush.getDistributors(getContext())
        android.util.Log.d(TAG, "Registering with distributors: $currentDistributors")
        
        // Update available distributors for the UI
        distributors.postValue(currentDistributors)
        
        if (currentDistributors.isEmpty()) {
            android.util.Log.d(TAG, "No distributors available")
            onRegistrationFailed()
            return
        }
        
        try {
            // Clear any existing endpoint before registering again
            endpoint.postValue(null)
            
            // Get the stored distributor if it exists
            val savedDistributor = UnifiedPush.getDistributor(getContext())
            android.util.Log.d(TAG, "Current saved distributor: $savedDistributor")
            
            // Special case for NextPush - it often needs a clean start
            if (savedDistributor.contains("nextpush", ignoreCase = true) || 
                currentDistributors.any { it.contains("nextpush", ignoreCase = true) }) {
                
                handleNextPushRegistration(savedDistributor, currentDistributors)
                return
            }
            
            if (currentDistributors.size == 1) {
                // Only one distributor available, use it
                val distributorToUse = currentDistributors[0]
                android.util.Log.d(TAG, "Only one distributor available, using: $distributorToUse")
                UnifiedPush.saveDistributor(getContext(), distributorToUse)
                UnifiedPush.registerApp(getContext())
            } else if (savedDistributor.isNotEmpty() && currentDistributors.contains(savedDistributor)) {
                // We have a saved distributor, use it
                android.util.Log.d(TAG, "Using saved distributor: $savedDistributor")
                UnifiedPush.saveDistributor(getContext(), savedDistributor)
                UnifiedPush.registerApp(getContext())
            } else {
                // Multiple distributors, let the system handle selection
                android.util.Log.d(TAG, "Multiple distributors, showing selection dialog")
                // This will trigger the OS app picker dialog
                
                // Clear any distributor selection
                UnifiedPush.saveDistributor(getContext(), "")
                
                // Then register
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    UnifiedPush.registerApp(getContext())
                }, 500)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Registration error", e)
            onRegistrationFailed()
        }
    }
    
    private fun handleNextPushRegistration(savedDistributor: String, currentDistributors: List<String>) {
        android.util.Log.d(TAG, "Detected NextPush, using deep fix with NextPushFixer")
        
        // Use the specialized NextPush fixer for a deeper fix
        val fixer = NextPushFixer(getContext())
        fixer.fixNextPush {
            // This is called when the fix is complete
            android.util.Log.d(TAG, "NextPush deep fix complete, proceeding with registration")
            
            // Run on main thread
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                // Find NextPush in the distributor list
                val nextPushPackage = currentDistributors.find { it.contains("nextpush", ignoreCase = true) }
                
                if (nextPushPackage != null) {
                    android.util.Log.d(TAG, "Registering with fixed NextPush: $nextPushPackage")
                    
                    // Save the distributor
                    UnifiedPush.saveDistributor(getContext(), nextPushPackage)
                    
                    // Register with a delay
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        // Try to register
                        UnifiedPush.registerApp(getContext())
                        
                        // Check for success after a delay
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            checkNextPushRegistration()
                        }, 5000)
                    }, 2000)
                } else {
                    android.util.Log.d(TAG, "NextPush not found in available distributors after fix")
                    fallbackToNtfy()
                }
            }
        }
    }
    
    /**
     * Fall back to NTFY distributor if available
     */
    private fun fallbackToNtfy() {
        android.util.Log.d(TAG, "Attempting to fall back to NTFY distributor")
        
        // Check if NTFY is available
        val distributors = UnifiedPush.getDistributors(getContext())
        val ntfyDistributor = distributors.find { 
            it.contains("ntfy", ignoreCase = true) || 
            it.contains("sh.elim", ignoreCase = true)
        }
        
        if (ntfyDistributor != null) {
            android.util.Log.d(TAG, "Using NTFY distributor: $ntfyDistributor")
            
            // Save the distributor
            UnifiedPush.saveDistributor(getContext(), ntfyDistributor)
            
            // Register with a delay
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                UnifiedPush.registerApp(getContext())
            }, 1000)
        } else {
            android.util.Log.d(TAG, "No suitable distributor found, trying default")
            
            // Just try registering with any distributor
            val anyDistributor = distributors.firstOrNull()
            if (anyDistributor != null) {
                UnifiedPush.saveDistributor(getContext(), anyDistributor)
                UnifiedPush.registerApp(getContext())
            } else {
                android.util.Log.e(TAG, "No distributors available")
                onRegistrationFailed()
            }
        }
    }
    
    /**
     * Check if NextPush registration was successful and retry if needed
     */
    private fun checkNextPushRegistration() {
        val upPrefs = getContext().getSharedPreferences("org.unifiedpush.android.connector_preferences", Context.MODE_PRIVATE)
        val token = upPrefs.getString("token", null)
        
        if (token == null) {
            android.util.Log.d(TAG, "NextPush registration check: No token found, retrying")
            
            // Try one more direct registration attempt
            val currentDistributors = UnifiedPush.getDistributors(getContext())
            val nextPushPackage = currentDistributors.find { it.contains("nextpush", ignoreCase = true) }
            
            if (nextPushPackage != null) {
                // Try once more with minimal delay
                UnifiedPush.registerApp(getContext())
            }
        } else {
            android.util.Log.d(TAG, "NextPush registration check: Token found, registration successful")
        }
    }
    
    /**
     * Configure NextPush specific preferences to avoid internal errors
     */
    private fun configureNextPushPreferences() {
        try {
            // Try direct NextPush app preferences configuration
            val nextPushSettings = getContext().getSharedPreferences("org.unifiedpush.distributor.nextpush_preferences", Context.MODE_PRIVATE)
            nextPushSettings.edit().apply {
                // Clear any existing values first
                clear()
                
                // Set to use Direct account type instead of SSO which is more reliable
                putString("account_type", "Direct")
                
                // Set a direct server URL for the NextPush instance
                // This should be updated to match your NextPush server
                putString("server_url", "https://nextpush.unifiedpush.org/")
                
                // Disable any gateway requirements
                putBoolean("use_matrix_gateway", false)
                putBoolean("bypass_matrix_gateway", true)
                
                // General settings that might help
                putBoolean("keep_alive", true)
                putString("keep_alive_interval", "60")
                
                // Clear device ID to force new registration
                putString("device_id", null)
                
                // Ensure battery optimization is disabled
                putBoolean("ignore_battery_optimization", true)
                
                // Apply changes
                apply()
            }
            
            // Also clear any device registration data to force a clean registration
            val appStore = getContext().getSharedPreferences("org.unifiedpush.distributor.nextpush.AppStore", Context.MODE_PRIVATE)
            appStore.edit().clear().apply()
            
            android.util.Log.d(TAG, "Successfully configured NextPush preferences")
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Could not configure NextPush app preferences: ${e.message}")
        }
    }

    fun handleMessage(message: ByteArray) {
        try {
            val messageStr = String(message)
            val json = JSONObject(messageStr)
            
            val title = json.optString("title", "Discord")
            val content = json.optString("content", "New message")
            val channelId = json.optString("channel_id", "")
            val guildId = json.optString("guild_id", "")
            
            showNotification(title, content, channelId, guildId)
        } catch (e: Exception) {
            showNotification("Discord", String(message), "", "")
            android.util.Log.e(TAG, "Error handling message", e)
        }
    }

    fun showNotification(title: String, content: String, channelId: String, guildId: String) {
        if (AppLifecycleTracker.isAppInForeground) {
            return
        }

        // Play notification sound
        try {
            val mediaPlayer = MediaPlayer.create(getContext(), R.raw.discord_notification)
            mediaPlayer.setOnCompletionListener { mp -> mp.release() }
            mediaPlayer.start()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error playing notification sound", e)
        }

        val intent = Intent(getContext(), MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = buildDiscordUrl(channelId, guildId).toUri()
        }
        
        val pendingIntent = PendingIntent.getActivity(
            getContext(),
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(getContext(), CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        // Using a fixed notification ID ensures we replace existing notifications
        // instead of creating new ones each time
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    fun onNewEndpoint(newEndpoint: String) {
        android.util.Log.d(TAG, "New endpoint: $newEndpoint")
        
        if (newEndpoint.isBlank()) {
            android.util.Log.e(TAG, "Received empty endpoint, treating as failure")
            onRegistrationFailed()
            return
        }
        
        endpoint.postValue(newEndpoint)
    }
    
    fun onRegistrationFailed() {
        android.util.Log.e(TAG, "Registration failed")
        endpoint.postValue(null)
        
        // Try to register with a different distributor after a delay
        val currentDistributor = UnifiedPush.getDistributor(getContext())
        val availableDistributors = UnifiedPush.getDistributors(getContext())
        
        if (availableDistributors.size > 1 && currentDistributor.isNotEmpty()) {
            val otherDistributors = availableDistributors.filter { it != currentDistributor }
            if (otherDistributors.isNotEmpty()) {
                val newDistributor = otherDistributors[0]
                android.util.Log.d(TAG, "Will try with different distributor: $newDistributor")
                
                // Try with a different distributor after a delay
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    UnifiedPush.saveDistributor(getContext(), newDistributor)
                    UnifiedPush.registerApp(getContext())
                }, 1000)
            }
        }
    }
    
    fun onUnregistered() {
        android.util.Log.d(TAG, "Unregistered from push service")
        endpoint.postValue(null)
    }

    private fun createNotificationChannel() {
        val soundUri = ("android.resource://" + getContext().packageName + "/raw/discord_notification").toUri()
        
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
            .build()
            
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Discord Messages",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Discord message notifications"
            enableLights(true)
            lightColor = Color.BLUE
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 250, 250)
            setShowBadge(true)
            setSound(soundUri, audioAttributes)
            setBypassDnd(true)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildDiscordUrl(channelId: String, guildId: String): String {
        return when {
            channelId.isNotEmpty() && guildId.isNotEmpty() -> 
                "https://discord.com/channels/$guildId/$channelId"
            channelId.isNotEmpty() -> 
                "https://discord.com/channels/@me/$channelId"
            else -> 
                "https://discord.com/app"
        }
    }
}