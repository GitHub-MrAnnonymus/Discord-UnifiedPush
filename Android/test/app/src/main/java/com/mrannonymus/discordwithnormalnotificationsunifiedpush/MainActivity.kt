package com.mrannonymus.discordwithnormalnotificationsunifiedpush

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.mrannonymus.discordwithnormalnotificationsunifiedpush.ui.theme.DiscordWithNormalNotificationsUnifiedPushTheme

@Composable
fun MainScreen(pushHelper: UnifiedPushHelper, prefsManager: PreferencesManager) {
    var showConfig by remember { mutableStateOf(!prefsManager.isConfigured) }

    when {
        !showConfig -> {
            DiscordWebView(modifier = Modifier.fillMaxSize())
        }
        else -> {
            UnifiedPushConfigScreen(
                pushHelper = pushHelper,
                onConfigured = { 
                    prefsManager.isConfigured = true
                    showConfig = false
                }
            )
        }
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var pushHelper: UnifiedPushHelper
    private lateinit var prefsManager: PreferencesManager
    private var webView: WebView? = null
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pushHelper = UnifiedPushHelper.getInstance(this)
        prefsManager = PreferencesManager.getInstance(this)
        
        setContent {
            DiscordWithNormalNotificationsUnifiedPushTheme {
                MainScreen(pushHelper, prefsManager)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (resultCode == RESULT_OK && data?.data != null) {
            fileChooserCallback?.onReceiveValue(arrayOf(data.data!!))
        } else {
            fileChooserCallback?.onReceiveValue(null)
        }
        fileChooserCallback = null
    }
}