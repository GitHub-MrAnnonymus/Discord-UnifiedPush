package to.us.charlesst.discord

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import androidx.core.net.toUri

object NotificationHelper {
    private const val CHANNEL_ID = "discord_test_channel"
    
    fun createNotificationChannel(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val soundUri = "android.resource://${context.packageName}/raw/notification".toUri()
        
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .build()
        
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Discord Test Channel",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Test channel for Discord notifications"
            enableLights(true)
            enableVibration(true)
            setSound(soundUri, audioAttributes)
        }
        
        notificationManager.createNotificationChannel(channel)
    }
} 