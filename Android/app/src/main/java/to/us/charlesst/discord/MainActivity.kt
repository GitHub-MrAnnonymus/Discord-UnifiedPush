package to.us.charlesst.discord

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import android.view.WindowManager


class MainActivity : AppCompatActivity() {
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var pushHelper: UnifiedPushHelper
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        pushHelper = UnifiedPushHelper.getInstance(this)
        preferencesManager = PreferencesManager(this)
        
        if (preferencesManager.isFirstLaunch()) {
            startActivity(Intent(this, UnifiedPushConfigScreen::class.java))
            finish()
            return
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
        
        val webView: WebView = findViewById(R.id.webview)
        
        // Set dark background color to prevent white flash
        webView.setBackgroundColor(Color.parseColor("#36393F"))
        
        CookieManager.getInstance().setAcceptCookie(true)
        val webSettings: WebSettings = webView.settings
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // Maintain dark background during page load
                view?.setBackgroundColor(Color.parseColor("#36393F"))
            }
            
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Keep dark background after page load completed
                view?.setBackgroundColor(Color.parseColor("#36393F"))
                
                // Apply dark background to page body
                view?.evaluateJavascript("""
                    document.body.style.backgroundColor = '#36393F';
                """.trimIndent(), null)
            }
        }
        webView.settings.userAgentString = "Android (+https://github.com/charles8191/discord)"
        webSettings.domStorageEnabled = true
        webSettings.allowFileAccess = true
        webSettings.mediaPlaybackRequiresUserGesture = false
        
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                request.grant(request.resources)
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
        }
        
        // Handle deep linking
        intent?.data?.let { uri ->
            webView.loadUrl(uri.toString())
        } ?: webView.loadUrl("https://discord.com/app")
    }
    
    override fun onBackPressed() {
        val webView: WebView = findViewById(R.id.webview)
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
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
}
