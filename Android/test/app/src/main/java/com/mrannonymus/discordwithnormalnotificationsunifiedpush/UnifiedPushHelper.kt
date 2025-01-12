package com.mrannonymus.discordwithnormalnotificationsunifiedpush

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import org.unifiedpush.android.connector.UnifiedPush
import java.lang.ref.WeakReference
import org.json.JSONObject
import androidx.core.app.NotificationCompat

class UnifiedPushHelper(context: Context) {
    private val appContext = context.applicationContext
    val endpoint: MutableState<String?> = mutableStateOf(null)
    val distributors: MutableState<List<String>> = mutableStateOf(emptyList())
    private val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    init {
        updateAvailableDistributors()
        createNotificationChannel()
    }
    
    private fun updateAvailableDistributors() {
        distributors.value = UnifiedPush.getDistributors(appContext)
    }
    
    fun register() {
        if (distributors.value.isEmpty()) {
            onRegistrationFailed()
            return
        }
        
        if (distributors.value.size == 1) {
            UnifiedPush.saveDistributor(appContext, distributors.value[0])
            UnifiedPush.registerApp(appContext)
        } else {
            UnifiedPush.saveDistributor(appContext, "")
            UnifiedPush.registerApp(appContext)
        }
    }
    
    fun onNewEndpoint(newEndpoint: String) {
        endpoint.value = newEndpoint
    }
    
    fun onRegistrationFailed() {
        endpoint.value = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
        }
    }

    private fun showNotification(title: String, content: String, channelId: String, guildId: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(buildDiscordUrl(channelId, guildId))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            appContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
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

    companion object {
        private const val CHANNEL_ID = "discord_messages"
        @Volatile
        private var instance: WeakReference<UnifiedPushHelper>? = null
        private val LOCK = Any()

        fun getInstance(context: Context): UnifiedPushHelper {
            val currentInstance = instance?.get()
            if (currentInstance != null) {
                return currentInstance
            }

            return synchronized(LOCK) {
                val newInstance = UnifiedPushHelper(context)
                instance = WeakReference(newInstance)
                newInstance
            }
        }
    }
}