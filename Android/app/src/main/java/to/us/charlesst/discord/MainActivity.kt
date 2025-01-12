package to.us.charlesst.discord

import android.content.Intent
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
import androidx.core.content.ContextCompat


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
        
        window.statusBarColor = ContextCompat.getColor(this, R.color.blurple)
        setContentView(R.layout.activity_main)
        val webView: WebView = findViewById(R.id.webview)
        CookieManager.getInstance().setAcceptCookie(true) // for Cloudflare
        val webSettings: WebSettings = webView.getSettings()
        webView.settings.javaScriptEnabled = true // for obvious reasons, Discord is a JS-heavy app
        webView.webViewClient = WebViewClient()
        webView.settings.userAgentString = "Android (+https://github.com/charles8191/discord)" // prevent fingerprinting by user-agent
        webSettings.setDomStorageEnabled(true) // used for tokens
        webSettings.setAllowFileAccess(true) // allow file picker
        webSettings.mediaPlaybackRequiresUserGesture = false // fix loading animation
        webView.webChromeClient = object : WebChromeClient() {
        override fun onPermissionRequest(request: PermissionRequest) {
            request.grant(request.resources)
        }

        override fun onShowFileChooser(
            vw: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams
        ): Boolean {
            fileChooserCallback?.onReceiveValue(null)
            fileChooserCallback = filePathCallback

            val selectionIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }

            val chooserIntent = Intent(Intent.ACTION_CHOOSER).apply {
                putExtra(Intent.EXTRA_INTENT, selectionIntent)
            }

            startActivityForResult(chooserIntent, 0)
            return true
        }
        }

        // Handle deep linking
        intent?.data?.let { uri ->
            webView.loadUrl(uri.toString())
        } ?: webView.loadUrl("https://discord.com/app")
    }
    override fun onBackPressed() { // fix back button
        val webView: WebView = findViewById(R.id.webview)
        if (webView.canGoBack()) {
            webView.goBack()
        }
        else {
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
