package to.us.charlesst.discord

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SetupActivity : AppCompatActivity() {
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var pushHelper: UnifiedPushHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        
        preferencesManager = PreferencesManager(this)
        pushHelper = UnifiedPushHelper.getInstance(this)
        
        val urlTextView = findViewById<TextView>(R.id.upUrlTextView)
        val continueButton = findViewById<Button>(R.id.continueButton)

        // Observe endpoint changes
        pushHelper.endpoint.observe(this) { endpoint ->
            urlTextView.text = if (endpoint != null) {
                getString(R.string.unified_push_url, endpoint)
            } else {
                getString(R.string.unified_push_failed)
            }
        }

        // Try to use current or default distributor
        pushHelper.register()

        continueButton.setOnClickListener {
            preferencesManager.setFirstLaunchComplete()
            startMainActivity()
        }
    }

    private fun startMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}