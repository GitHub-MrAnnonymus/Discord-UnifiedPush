package com.mrannonymus.discordwithnormalnotificationsunifiedpush

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.webkit.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.mrannonymus.discordwithnormalnotificationsunifiedpush.ui.theme.DiscordGrey

@Composable
fun DiscordWebView(modifier: Modifier = Modifier) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var fileChooserCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val context = LocalContext.current
    
    DisposableEffect(Unit) {
        WebView.setWebContentsDebuggingEnabled(true)
        onDispose {
            webView?.destroy()
            fileChooserCallback?.onReceiveValue(null)
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                webView = this
                
                // Set background color to match Discord dark theme to prevent white flash
                setBackgroundColor(DiscordGrey.toArgb())
                
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    allowFileAccess = true
                    allowContentAccess = true
                    
                    // Desktop mode for better rendering
                    userAgentString = "Android (+https://github.com/your-username/your-repo)"
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onPermissionRequest(request: PermissionRequest) {
                        request.grant(request.resources)
                    }

                    override fun onShowFileChooser(
                        webView: WebView,
                        callback: ValueCallback<Array<Uri>>,
                        params: FileChooserParams
                    ): Boolean {
                        fileChooserCallback?.onReceiveValue(null)
                        fileChooserCallback = callback
                        
                        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                        }
                        
                        context.startActivity(Intent.createChooser(intent, "Choose File"))
                        return true
                    }
                }

                webViewClient = DiscordWebViewClient()
                loadUrl("https://discord.com/app")
            }
        }
    )
}