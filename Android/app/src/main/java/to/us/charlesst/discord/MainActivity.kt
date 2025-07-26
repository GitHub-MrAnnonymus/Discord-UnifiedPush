package to.us.charlesst.discord

import android.Manifest
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat


class MainActivity : AppCompatActivity() {
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var pushHelper: UnifiedPushHelper
    private lateinit var webView: WebView
    
    // Permission request for notifications
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, 
                "Notification permission denied. You may not receive message notifications.", 
                Toast.LENGTH_LONG).show()
        }
    }
    
    // Permission request for WebRTC
    private val requestWebRTCPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        
        if (cameraGranted && audioGranted) {
            Toast.makeText(this, "WebRTC permissions granted - Voice/Video calls enabled", Toast.LENGTH_LONG).show()
        } else if (audioGranted) {
            Toast.makeText(this, "Audio permission granted - Voice calls enabled", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "WebRTC permissions denied - Voice/Video calls may not work", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        pushHelper = UnifiedPushHelper.getInstance(this)
        preferencesManager = PreferencesManager(this)
        
        if (preferencesManager.isFirstLaunch()) {
            startActivity(Intent(this, UnifiedPushConfigScreen::class.java))
            finish()
            return
        }
        
        // Check notification permission
        checkNotificationPermission()
        
        // Check WebRTC permissions
        checkWebRTCPermissions()
        
        // Check if notification style preference has been set
        if (!preferencesManager.isNotificationStyleSet()) {
            showNotificationStylePrompt()
        }
        
        // Set up window insets
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        
        setContentView(R.layout.activity_main)
        
        // Add padding for system bars using ViewCompat
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(0, insets.top, 0, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
        
        // Initialize notification helpers
        NotificationHelper.createNotificationChannel(this)
        
        webView = findViewById(R.id.webview)
        setupWebView()
        
        // Handle deep linking
        intent?.data?.let { uri ->
            webView.loadUrl(uri.toString())
        } ?: webView.loadUrl("https://discord.com/app")
    }
    
    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission already granted
                }
                ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) -> {
                    // Show permission explanation dialog
                    AlertDialog.Builder(this)
                        .setTitle("Notification Permission")
                        .setMessage("This app needs notification permission to alert you about new Discord messages.")
                        .setPositiveButton("Grant Permission") { _, _ ->
                            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        .setNegativeButton("Not Now") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .show()
                }
                else -> {
                    // Directly request permission
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }
    
    private fun showNotificationStylePrompt() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.notification_style_dialog, null)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.notificationStyleGroup)
        
        // Set the current style as selected
        val currentStyle = preferencesManager.getNotificationStyle()
        when (currentStyle) {
            PreferencesManager.NOTIFICATION_STYLE_SINGLE -> 
                dialogView.findViewById<RadioButton>(R.id.styleSingle).isChecked = true
            PreferencesManager.NOTIFICATION_STYLE_MULTI -> 
                dialogView.findViewById<RadioButton>(R.id.styleMulti).isChecked = true
            PreferencesManager.NOTIFICATION_STYLE_HYBRID -> 
                dialogView.findViewById<RadioButton>(R.id.styleHybrid).isChecked = true
        }
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.DiscordAlertDialogTheme)
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton("Save") { _, _ ->
                // Save the selected notification style
                val selectedStyle = when (radioGroup.checkedRadioButtonId) {
                    R.id.styleSingle -> PreferencesManager.NOTIFICATION_STYLE_SINGLE
                    R.id.styleMulti -> PreferencesManager.NOTIFICATION_STYLE_MULTI
                    R.id.styleHybrid -> PreferencesManager.NOTIFICATION_STYLE_HYBRID
                    else -> PreferencesManager.NOTIFICATION_STYLE_SINGLE
                }
                
                preferencesManager.setNotificationStyle(selectedStyle)
                
                // Show a toast to confirm
                val styleName = when (selectedStyle) {
                    PreferencesManager.NOTIFICATION_STYLE_SINGLE -> "Single Notification"
                    PreferencesManager.NOTIFICATION_STYLE_MULTI -> "Multiple Notifications"
                    PreferencesManager.NOTIFICATION_STYLE_HYBRID -> "Hybrid Style"
                    else -> "Default Style"
                }
                Toast.makeText(this, "Using $styleName", Toast.LENGTH_SHORT).show()
            }
            .show()
    }
    
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)
        
        if (requestCode == 0 && resultCode == RESULT_OK && intent != null && intent.data != null) {
            fileChooserCallback?.onReceiveValue(arrayOf(Uri.parse(intent.dataString)))
        } else {
            fileChooserCallback?.onReceiveValue(null)
        }
        fileChooserCallback = null
    }
    
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Update user agent when orientation changes
        updateUserAgent()
    }
    
    private fun checkWebRTCPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            requestWebRTCPermissions.launch(permissionsToRequest.toTypedArray())
        }
    }
    
    private fun getOrientationBasedUserAgent(): String {
        return when (resources.configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                // Desktop Chrome user agent for WebRTC functionality
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            }
            else -> {
                // Android user agent for mobile UI
                "Mozilla/5.0 (Linux; Android 13; SM-G998B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 (+https://github.com/charles8191/discord)"
            }
        }
    }
    
    private fun updateUserAgent() {
        val newUserAgent = getOrientationBasedUserAgent()
        if (webView.settings.userAgentString != newUserAgent) {
            webView.settings.userAgentString = newUserAgent
            // Reload to apply new user agent
            webView.reload()
            
            val orientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                "Desktop mode (WebRTC enabled)"
            } else {
                "Mobile mode"
            }
            Toast.makeText(this, "Switched to $orientation", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupWebView() {
        // Set dark background color to prevent white flash
        webView.setBackgroundColor(Color.parseColor("#36393F"))
        
        // Enable debugging for development
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        
        CookieManager.getInstance().setAcceptCookie(true)
        val webSettings: WebSettings = webView.settings
        
        // Basic WebView settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.allowFileAccess = true
        webSettings.allowContentAccess = true
        webSettings.allowFileAccessFromFileURLs = true
        webSettings.allowUniversalAccessFromFileURLs = true
        
        // WebRTC specific settings
        webSettings.mediaPlaybackRequiresUserGesture = false
        webSettings.databaseEnabled = true
        webSettings.setGeolocationEnabled(true)
        
        // Set initial user agent based on current orientation
        webSettings.userAgentString = getOrientationBasedUserAgent()
        
        // Enhanced settings for better performance
        webSettings.cacheMode = WebSettings.LOAD_DEFAULT
        webSettings.setRenderPriority(WebSettings.RenderPriority.HIGH)
        webSettings.setEnableSmoothTransition(true)
        
        // Mixed content mode for HTTPS sites with HTTP resources
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }
        
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // Maintain dark background during page load
                view?.setBackgroundColor(Color.parseColor("#36393F"))
            }
            
            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                android.util.Log.d("DiscordWebView", "Loading URL: $url")
                
                // Check if this is a Discord internal URL
                val isDiscordInternal = url.startsWith("https://discord.com") || 
                                       url.startsWith("https://cdn.discordapp.com") ||
                                       url.startsWith("https://media.discordapp.net") ||
                                       url.startsWith("https://discordapp.com") ||
                                       url.contains("discord.com") ||
                                       url.contains("discordapp.com") ||
                                       url.contains("discordapp.net")
                
                return if (isDiscordInternal) {
                    // Handle Discord URLs internally in the WebView
                    android.util.Log.d("DiscordWebView", "Loading Discord internal URL in WebView: $url")
                    view?.loadUrl(url)
                    true
                } else {
                    // Open external URLs in system browser
                    android.util.Log.d("DiscordWebView", "Opening external URL in system browser: $url")
                    try {
                        val context = view?.context
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context?.startActivity(intent)
                        return true
                    } catch (e: Exception) {
                        android.util.Log.e("DiscordWebView", "Failed to open external URL: $url", e)
                        // Fallback to loading in WebView if browser intent fails
                        view?.loadUrl(url)
                        return true
                    }
                }
            }
            
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Keep dark background after page load completed
                view?.setBackgroundColor(Color.parseColor("#36393F"))
                
                // Apply dark background to page body and enable WebRTC APIs
                view?.evaluateJavascript("""
                    // Set dark background
                    document.body.style.backgroundColor = '#36393F';
                    
                    // Enable getUserMedia polyfill if needed
                    if (!navigator.mediaDevices && navigator.getUserMedia) {
                        navigator.mediaDevices = {};
                        navigator.mediaDevices.getUserMedia = function(constraints) {
                            var getUserMedia = navigator.getUserMedia || navigator.webkitGetUserMedia || navigator.mozGetUserMedia;
                            if (!getUserMedia) {
                                return Promise.reject(new Error('getUserMedia is not implemented in this browser'));
                            }
                            return new Promise(function(resolve, reject) {
                                getUserMedia.call(navigator, constraints, resolve, reject);
                            });
                        };
                    }
                    
                    // Log WebRTC capability
                    console.log('WebRTC support: ', {
                        getUserMedia: !!(navigator.mediaDevices && navigator.mediaDevices.getUserMedia),
                        RTCPeerConnection: !!(window.RTCPeerConnection || window.mozRTCPeerConnection || window.webkitRTCPeerConnection),
                        userAgent: navigator.userAgent
                    });
                """.trimIndent(), null)
            }
        }
        
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                // Grant WebRTC permissions automatically if app permissions are granted
                val appPermissions = request.resources.all { resource ->
                    when (resource) {
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> 
                            ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> 
                            ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        else -> true
                    }
                }
                
                if (appPermissions) {
                    request.grant(request.resources)
                } else {
                    request.deny()
                    Toast.makeText(this@MainActivity, "WebRTC permissions needed for voice/video calls", Toast.LENGTH_LONG).show()
                }
            }
            
            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                fileChooserCallback = filePathCallback
                val intent = fileChooserParams.createIntent()
                try {
                    startActivityForResult(intent, 0)
                } catch (e: ActivityNotFoundException) {
                    fileChooserCallback = null
                    return false
                }
                return true
            }
            
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: android.webkit.GeolocationPermissions.Callback?
            ) {
                callback?.invoke(origin, true, false)
            }
        }
    }
}
