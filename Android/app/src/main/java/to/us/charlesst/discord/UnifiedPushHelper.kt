package to.us.charlesst.discord

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData
import org.unifiedpush.android.connector.UnifiedPush
import org.json.JSONObject
import java.lang.ref.WeakReference

class UnifiedPushHelper private constructor(context: Context) {
    private val contextRef = WeakReference(context.applicationContext)
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    val endpoint = MutableLiveData<String?>(null)
    val distributors = MutableLiveData<List<String>>(emptyList())
    
    companion object {
        @Volatile
        private var instance: UnifiedPushHelper? = null
        private const val CHANNEL_ID = "discord_messages"
        
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
    
    private fun updateAvailableDistributors() {
        val currentDistributors = UnifiedPush.getDistributors(getContext())
        android.util.Log.d("UnifiedPush", "Found distributors: $currentDistributors")
        distributors.postValue(currentDistributors)
    }
    
    fun register() {
        val currentDistributors = UnifiedPush.getDistributors(getContext())
        android.util.Log.d("UnifiedPush", "Registering with distributors: $currentDistributors")
        
        if (currentDistributors.isEmpty()) {
            android.util.Log.d("UnifiedPush", "No distributors available")
            onRegistrationFailed()
            return
        }
        
        if (currentDistributors.size == 1) {
            android.util.Log.d("UnifiedPush", "Using distributor: ${currentDistributors[0]}")
            UnifiedPush.saveDistributor(getContext(), currentDistributors[0])
            UnifiedPush.registerApp(getContext())
        } else {
            android.util.Log.d("UnifiedPush", "Multiple distributors, showing picker")
            UnifiedPush.saveDistributor(getContext(), "")
            UnifiedPush.registerApp(getContext())
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
            android.util.Log.e("UnifiedPush", "Error handling message", e)
        }
    }

    private fun showNotification(title: String, content: String, channelId: String, guildId: String) {
        val intent = Intent(getContext(), MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse(buildDiscordUrl(channelId, guildId))
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
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
    
    fun onNewEndpoint(newEndpoint: String) {
        android.util.Log.d("UnifiedPush", "New endpoint: $newEndpoint")
        endpoint.postValue(newEndpoint)
    }
    
    fun onRegistrationFailed() {
        android.util.Log.d("UnifiedPush", "Registration failed")
        endpoint.postValue(null)
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
            setShowBadge(true)
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