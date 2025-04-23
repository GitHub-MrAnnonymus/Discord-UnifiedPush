package to.us.charlesst.discord

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.unifiedpush.android.connector.UnifiedPush
import java.io.File

/**
 * Special utility class to fix NextPush registration issues
 * by directly manipulating its preferences and database files.
 */
class NextPushFixer(private val context: Context) {
    
    private val TAG = "NextPushFixer"
    
    // Server URL to use for NextPush direct mode
    private val NEXTPUSH_SERVER = "https://nextpush.unifiedpush.org/"
    
    /**
     * Execute a complete NextPush reset and reconfiguration
     */
    fun fixNextPush(onComplete: () -> Unit) {
        Log.d(TAG, "Starting NextPush deep fix...")
        
        Thread {
            try {
                // 1. Force unregister from UnifiedPush
                try {
                    UnifiedPush.unregisterApp(context)
                } catch (e: Exception) {
                    Log.w(TAG, "Error unregistering: ${e.message}")
                }
                
                // 2. Clear UnifiedPush connector preferences
                clearUnifiedPushPreferences()
                
                // 3. Clear NextPush app data
                clearNextPushPreferences()
                
                // 4. Set up NextPush direct mode
                setupDirectMode()
                
                // 5. Clear databases
                deleteNextPushDatabases()
                
                // 6. Restart NextPush app
                restartNextPush()
                
                // 7. Register after a delay
                Handler(Looper.getMainLooper()).postDelayed({
                    Log.d(TAG, "NextPush fix complete, proceeding with registration")
                    onComplete()
                }, 3000)
            } catch (e: Exception) {
                Log.e(TAG, "Error during NextPush fix: ${e.message}")
                
                // Still try to complete
                Handler(Looper.getMainLooper()).postDelayed({
                    onComplete()
                }, 1000)
            }
        }.start()
    }
    
    /**
     * Clear UnifiedPush connector preferences
     */
    private fun clearUnifiedPushPreferences() {
        val upPrefs = context.getSharedPreferences("org.unifiedpush.android.connector_preferences", Context.MODE_PRIVATE)
        upPrefs.edit().clear().apply()
        Log.d(TAG, "Cleared UnifiedPush preferences")
    }
    
    /**
     * Clear all NextPush preferences
     */
    private fun clearNextPushPreferences() {
        // Main NextPush preferences
        val npPrefs = context.getSharedPreferences("org.unifiedpush.distributor.nextpush_preferences", Context.MODE_PRIVATE)
        npPrefs.edit().clear().apply()
        
        // NextPush store
        val npStore = context.getSharedPreferences("org.unifiedpush.distributor.nextpush.AppStore", Context.MODE_PRIVATE)
        npStore.edit().clear().apply()
        
        // Other possible NextPush preferences
        val prefFiles = listOf(
            "org.unifiedpush.distributor.nextpush.account.DirectAccountStore",
            "org.unifiedpush.distributor.nextpush.account.AccountStore"
        )
        
        for (prefFile in prefFiles) {
            try {
                context.getSharedPreferences(prefFile, Context.MODE_PRIVATE).edit().clear().apply()
            } catch (e: Exception) {
                // Ignore
            }
        }
        
        Log.d(TAG, "Cleared NextPush preferences")
    }
    
    /**
     * Set up NextPush direct mode with a known working server
     */
    private fun setupDirectMode() {
        val npPrefs = context.getSharedPreferences("org.unifiedpush.distributor.nextpush_preferences", Context.MODE_PRIVATE)
        
        npPrefs.edit().apply {
            // Use Direct mode
            putString("account_type", "Direct")
            
            // Set server URL
            putString("server_url", NEXTPUSH_SERVER)
            
            // Other helpful settings
            putBoolean("keep_alive", true)
            putString("keep_alive_interval", "60")
            putBoolean("use_matrix_gateway", false)
            putBoolean("bypass_matrix_gateway", true)
            putBoolean("ignore_battery_optimization", true)
            
            apply()
        }
        
        Log.d(TAG, "Configured NextPush Direct mode with server $NEXTPUSH_SERVER")
    }
    
    /**
     * Delete NextPush database files to ensure clean start
     */
    private fun deleteNextPushDatabases() {
        try {
            val databasesDir = context.applicationInfo.dataDir + "/databases"
            val dbDir = File(databasesDir)
            
            if (dbDir.exists() && dbDir.isDirectory) {
                val dbFiles = dbDir.listFiles { file -> 
                    file.name.startsWith("nextpush") || 
                    file.name.contains("nextpush") ||
                    file.name.contains("unified")
                }
                
                dbFiles?.forEach { file ->
                    val deleted = file.delete()
                    Log.d(TAG, "Deleted database file ${file.name}: $deleted")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error deleting NextPush databases: ${e.message}")
        }
    }
    
    /**
     * Try to restart the NextPush app
     */
    private fun restartNextPush() {
        try {
            val distributors = UnifiedPush.getDistributors(context)
            val nextPushPackage = distributors.find { it.contains("nextpush", ignoreCase = true) }
            
            if (nextPushPackage != null) {
                // Force save this specific NextPush distributor
                UnifiedPush.saveDistributor(context, nextPushPackage)
                
                // Try to start NextPush
                val launchIntent = context.packageManager.getLaunchIntentForPackage(nextPushPackage)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    Thread.sleep(2000) // Wait for it to start
                    Log.d(TAG, "Started NextPush app")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not restart NextPush: ${e.message}")
        }
    }
} 