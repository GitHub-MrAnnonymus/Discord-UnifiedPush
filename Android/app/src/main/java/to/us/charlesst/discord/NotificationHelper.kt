package to.us.charlesst.discord

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.app.NotificationCompat

object NotificationHelper {
    private const val CHANNEL_ID = "discord_test_channel"
    
    fun createNotificationChannel(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val soundUri = Uri.parse("android.resource://${context.packageName}/raw/notification")
        
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
    
    fun showTestNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val soundUri = Uri.parse("android.resource://${context.packageName}/raw/notification")
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Discord Test")
            .setContentText("This is a test notification with sound")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(soundUri)
            .build()
        
        notificationManager.notify(1001, notification)
    }
} 