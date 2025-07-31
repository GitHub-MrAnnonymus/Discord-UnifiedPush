package to.us.charlesst.discord

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.lifecycle.MutableLiveData
import com.google.crypto.tink.subtle.EllipticCurves
import org.json.JSONObject
import org.unifiedpush.android.connector.UnifiedPush
import java.lang.ref.WeakReference
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Random

private const val TAG = "UnifiedPush"

class UnifiedPushHelper private constructor(context: Context) {
    private val contextRef = WeakReference(context.applicationContext)
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val preferencesManager = PreferencesManager(context)
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
        android.util.Log.d(TAG, "Available distributors: $currentDistributors")
        
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
            
            // Check if there's already a saved distributor
            val savedDistributor = UnifiedPush.getSavedDistributor(getContext())
            android.util.Log.d(TAG, "Saved distributor: $savedDistributor")
            
            if (savedDistributor != null && currentDistributors.contains(savedDistributor)) {
                // Use existing saved distributor
                android.util.Log.d(TAG, "Using existing saved distributor: $savedDistributor")
                preferencesManager.setCurrentDistributor(savedDistributor)
                
                // Use VAPID only if enabled by user preference
                if (preferencesManager.getVapidEnabled()) {
                    val vapidKey = getVapidPublicKey()
                    if (vapidKey != null) {
                        android.util.Log.d(TAG, "Registering with VAPID (user enabled)")
                        UnifiedPush.register(getContext(), "", vapid = vapidKey)
                    } else {
                        android.util.Log.d(TAG, "VAPID enabled but key generation failed, registering without")
                        UnifiedPush.register(getContext())
                    }
                } else {
                    android.util.Log.d(TAG, "VAPID disabled by user preference, registering without")
                    UnifiedPush.register(getContext())
                }
                android.util.Log.d(TAG, "Registration initiated with saved distributor") 
                
            } else {
                // Try to use the first available distributor (like reference app does)
                val firstDistributor = currentDistributors[0]
                android.util.Log.d(TAG, "Saving first available distributor: $firstDistributor")
                
                UnifiedPush.saveDistributor(getContext(), firstDistributor)
                preferencesManager.setCurrentDistributor(firstDistributor)
                
                // Use VAPID only if enabled by user preference for new distributors too
                if (preferencesManager.getVapidEnabled()) {
                    val vapidKey = getVapidPublicKey()
                    if (vapidKey != null) {
                        android.util.Log.d(TAG, "Registering new distributor with VAPID (user enabled)")
                        UnifiedPush.register(getContext(), "", vapid = vapidKey)
                    } else {
                        android.util.Log.d(TAG, "VAPID enabled but key generation failed, registering without")
                        UnifiedPush.register(getContext())
                    }
                } else {
                    android.util.Log.d(TAG, "VAPID disabled by user preference, registering without")
                    UnifiedPush.register(getContext())
                }
                android.util.Log.d(TAG, "Registration initiated with new distributor")
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
        
        val displayTitle = title
        val displayContent = content.ifBlank { "New message" }

        val notificationBuilder = NotificationCompat.Builder(getContext(), CHANNEL_ID)
            .setContentTitle(displayTitle)
            .setContentText(displayContent)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
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
        
        // Use BigTextStyle for content-rich notifications
        if (content.length > 40 || content.contains("\n")) {
            notificationBuilder.setStyle(NotificationCompat.BigTextStyle().bigText(displayContent))
        }
        
        val notification = notificationBuilder.build()

        // Determine notification ID based on selected style
        when (notificationStyle) {
            PreferencesManager.NOTIFICATION_STYLE_MULTI -> {
                // Multiple notifications with content - use random ID for each notification
                val notificationId = random.nextInt(10000) + 1001 // Avoid using the fixed ID
                notificationManager.notify(notificationId, notification)
            }
            PreferencesManager.NOTIFICATION_STYLE_HYBRID -> {
                // Hybrid - single notification with content updates
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
            else -> {
                // Default to hybrid behavior for any unknown styles
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
        val currentDistributor = UnifiedPush.getSavedDistributor(getContext()) ?: ""
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
                    UnifiedPush.register(getContext())
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

    // VAPID Support Methods
    fun generateVapidKeys(): String? {
        try {
            android.util.Log.d(TAG, "Generating VAPID keys...")
            
            val keyPairGenerator = KeyPairGenerator.getInstance("EC")
            val ecSpec = ECGenParameterSpec("secp256r1")
            keyPairGenerator.initialize(ecSpec, SecureRandom())
            
            val keyPair = keyPairGenerator.generateKeyPair()
            val publicKey = keyPair.public as ECPublicKey
            
            // Use Tink's EllipticCurves to properly encode the public key (like reference app)
            val points = EllipticCurves.pointEncode(
                EllipticCurves.CurveType.NIST_P256,
                EllipticCurves.PointFormatType.UNCOMPRESSED,
                publicKey.w
            )
            
            // Base64 encode with URL_SAFE, NO_WRAP, and NO_PADDING (like reference app)
            val vapidPublicKey = Base64.encodeToString(
                points, 
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
            
            android.util.Log.d(TAG, "Generated VAPID public key: $vapidPublicKey")
            
            // Store the public key
            preferencesManager.setVapidPublicKey(vapidPublicKey)
            
            return vapidPublicKey
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to generate VAPID keys", e)
            return null
        }
    }
    
    fun getVapidPublicKey(): String? {
        // Only return or generate VAPID keys if VAPID is enabled
        if (!preferencesManager.getVapidEnabled()) {
            return null
        }
        
        var vapidKey = preferencesManager.getVapidPublicKey()
        
        if (vapidKey == null) {
            // Generate new VAPID keys if none exist and VAPID is enabled
            vapidKey = generateVapidKeys()
        }
        
        return vapidKey
    }
}