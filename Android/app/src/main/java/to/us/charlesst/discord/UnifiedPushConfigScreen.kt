package to.us.charlesst.discord

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class UnifiedPushConfigScreen : AppCompatActivity() {
    private lateinit var pushHelper: UnifiedPushHelper
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var urlTextView: TextView
    private lateinit var copyButton: Button
    private lateinit var continueButton: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = ContextCompat.getColor(this, R.color.blurple)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.blurple)
        setContentView(R.layout.activity_setup)
        
        pushHelper = UnifiedPushHelper.getInstance(this)
        preferencesManager = PreferencesManager(this)
        
        urlTextView = findViewById(R.id.upUrlTextView)
        copyButton = findViewById(R.id.copyButton)
        continueButton = findViewById(R.id.continueButton)
        
        copyButton.setOnClickListener {
            pushHelper.endpoint.value?.let { endpoint ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("UnifiedPush Endpoint", endpoint)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "URL copied!", Toast.LENGTH_SHORT).show()
            }
        }
        
        continueButton.setOnClickListener {
            preferencesManager.setFirstLaunchComplete()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
        
        pushHelper.endpoint.observe(this) { endpoint ->
            if (endpoint != null) {
                urlTextView.text = getString(R.string.unified_push_url, endpoint)
                copyButton.isEnabled = true
            }
        }
        
        pushHelper.register()
    }
}