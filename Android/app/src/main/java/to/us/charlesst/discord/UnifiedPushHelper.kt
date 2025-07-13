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
import java.util.Random

private const val TAG = "UnifiedPush"

class UnifiedPushHelper private constructor(context: Context) {
    private val contextRef = WeakReference(context.applicationContext)
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val preferencesManager = PreferencesManager(context)
    private val random = Random()
    
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
            
            if (currentDistributors.size == 1) {
                // Only one distributor available, use it
                val distributorToUse = currentDistributors[0]
                android.util.Log.d(TAG, "Only one distributor available, using: $distributorToUse")
                UnifiedPush.saveDistributor(getContext(), distributorToUse)
                preferencesManager.setCurrentDistributor(distributorToUse)
                UnifiedPush.registerApp(getContext())
            } else if (savedDistributor.isNotEmpty() && currentDistributors.contains(savedDistributor)) {
                // We have a saved distributor, use it
                android.util.Log.d(TAG, "Using saved distributor: $savedDistributor")
                UnifiedPush.saveDistributor(getContext(), savedDistributor)
                preferencesManager.setCurrentDistributor(savedDistributor)
                UnifiedPush.registerApp(getContext())
            } else {
                // Multiple distributors, let the system handle selection
                android.util.Log.d(TAG, "Multiple distributors, showing selection dialog")
                // This will trigger the OS app picker dialog
                
                // Clear any distributor selection
                UnifiedPush.saveDistributor(getContext(), "")
                preferencesManager.setCurrentDistributor("")
                
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
    


    fun handleMessage(message: ByteArray) {
        try {
            val messageStr = String(message)
            android.util.Log.d(TAG, "Received raw message: $messageStr")
            
            // First try parsing as JSON
            val json = try {
                JSONObject(messageStr)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error parsing JSON, using raw message", e)
                // If not JSON, wrap the plain text in a JSON object
                JSONObject().apply {
                    put("content", messageStr)
                    put("title", "Discord")
                }
            }
            
            val title = json.optString("title", "Discord")
            val content = json.optString("content", messageStr) // Use raw message as content if not in JSON
            val channelId = json.optString("channel_id", "")
            val guildId = json.optString("guild_id", "")
            val sender = json.optString("sender", "")
            
            android.util.Log.d(TAG, "Parsed notification - Title: $title, Content: $content, Sender: $sender")
            
            // Construct a better title if sender is available
            val notificationTitle = if (sender.isNotEmpty()) {
                if (title != "Discord") "$title from $sender" else "Message from $sender"
            } else {
                title
            }
            
            // Always show notification regardless of content
            showNotification(notificationTitle, content, channelId, guildId)
            android.util.Log.d(TAG, "Successfully showed notification")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Critical error handling message", e)
            // Last resort - show raw message
            try {
                showNotification("Discord", String(message), "", "")
                android.util.Log.d(TAG, "Showed fallback notification with raw message")
            } catch (e2: Exception) {
                android.util.Log.e(TAG, "Even fallback notification failed", e2)
            }
        }
    }

    fun showNotification(title: String, content: String, channelId: String, guildId: String) {
        if (AppLifecycleTracker.isAppInForeground) {
            return
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
        
        // Get notification style
        val notificationStyle = preferencesManager.getNotificationStyle()
        
        // For SINGLE style, always use generic notification without content
        val displayTitle = if (notificationStyle == PreferencesManager.NOTIFICATION_STYLE_SINGLE) {
            "Discord"
        } else {
            title
        }
        
        // For SINGLE style, don't show message content
        val displayContent = if (notificationStyle == PreferencesManager.NOTIFICATION_STYLE_SINGLE) {
            "New Discord notification"
        } else if (content.isBlank()) {
            "New message"
        } else {
            content
        }

        val notificationBuilder = NotificationCompat.Builder(getContext(), CHANNEL_ID)
            .setContentTitle(displayTitle)
            .setContentText(displayContent)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(getNotificationVisibility())
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
        
        // Create a public version for lock screen (without sensitive content)
        val publicNotification = NotificationCompat.Builder(getContext(), CHANNEL_ID)
            .setContentTitle("Discord")
            .setContentText("New message received")
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()
        
        notificationBuilder.setPublicVersion(publicNotification)
        
        // Only use BigTextStyle for content-rich notifications in multi and hybrid styles
        if (notificationStyle != PreferencesManager.NOTIFICATION_STYLE_SINGLE) {
            if (content.length > 40 || content.contains("\n")) {
                notificationBuilder.setStyle(NotificationCompat.BigTextStyle().bigText(displayContent))
            }
        }
        
        val notification = notificationBuilder.build()

        // Determine notification ID based on selected style
        when (notificationStyle) {
            PreferencesManager.NOTIFICATION_STYLE_SINGLE -> {
                // Current approach - single notification with timestamp update
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
            PreferencesManager.NOTIFICATION_STYLE_MULTI -> {
                // Multiple notifications with content - use random ID for each notification
                val notificationId = random.nextInt(10000) + 1001 // Avoid using the fixed ID
                notificationManager.notify(notificationId, notification)
            }
            PreferencesManager.NOTIFICATION_STYLE_HYBRID -> {
                // Hybrid - single notification with content updates
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
        }
    }
    
    fun onNewEndpoint(newEndpoint: String) {
        android.util.Log.d(TAG, "New endpoint: $newEndpoint")
        
        if (newEndpoint.isBlank()) {
            android.util.Log.e(TAG, "Received empty endpoint, treating as failure")
            onRegistrationFailed()
            return
        }
        
        endpoint.postValue(newEndpoint)
        preferencesManager.setCurrentEndpoint(newEndpoint)
    }
    
    fun onRegistrationFailed() {
        android.util.Log.e(TAG, "Registration failed")
        endpoint.postValue(null)
        preferencesManager.setCurrentEndpoint("")
        
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
                    preferencesManager.setCurrentDistributor(newDistributor)
                    UnifiedPush.registerApp(getContext())
                }, 1000)
            }
        }
    }
    
    fun onUnregistered() {
        android.util.Log.d(TAG, "Unregistered from push service")
        endpoint.postValue(null)
        preferencesManager.setCurrentEndpoint("")
    }

    private fun createNotificationChannel() {
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
            // Respect system lock screen notification settings
            lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            // No custom sound set, will use system default
        }
        notificationManager.createNotificationChannel(channel)
    }
    
    private fun getNotificationVisibility(): Int {
        // Check the user's notification style preference
        val notificationStyle = preferencesManager.getNotificationStyle()
        
        return when (notificationStyle) {
            PreferencesManager.NOTIFICATION_STYLE_SINGLE -> {
                // For single notification style, don't show content on lock screen
                NotificationCompat.VISIBILITY_PRIVATE
            }
            PreferencesManager.NOTIFICATION_STYLE_MULTI, 
            PreferencesManager.NOTIFICATION_STYLE_HYBRID -> {
                // For multi and hybrid styles, use PRIVATE to respect system settings
                // This will show content only if user allows it in system settings
                NotificationCompat.VISIBILITY_PRIVATE
            }
            else -> NotificationCompat.VISIBILITY_PRIVATE
        }
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