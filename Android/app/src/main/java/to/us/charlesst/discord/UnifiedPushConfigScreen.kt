package to.us.charlesst.discord

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.unifiedpush.android.connector.UnifiedPush

class UnifiedPushConfigScreen : AppCompatActivity() {
    private lateinit var pushHelper: UnifiedPushHelper
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var urlTextView: TextView
    private lateinit var copyButton: Button
    private lateinit var continueButton: Button
    private lateinit var statusTextView: TextView
    private lateinit var retryButton: Button
    private lateinit var selectDistributorButton: Button
    private lateinit var notificationStyleButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Use modern window insets handling instead of deprecated statusBarColor
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_setup)

        pushHelper = UnifiedPushHelper.getInstance(this)
        preferencesManager = PreferencesManager(this)

        urlTextView = findViewById(R.id.upUrlTextView)
        copyButton = findViewById(R.id.copyButton)
        continueButton = findViewById(R.id.continueButton)
        statusTextView = findViewById(R.id.statusTextView)
        retryButton = findViewById(R.id.retryButton)
        selectDistributorButton = findViewById(R.id.selectDistributorButton)
        notificationStyleButton = findViewById(R.id.notificationStyleButton)

        copyButton.setOnClickListener {
            pushHelper.endpoint.value?.let { endpoint ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText(getString(R.string.unifiedpush_endpoint), endpoint)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, R.string.url_copied, Toast.LENGTH_SHORT).show()
            } ?: run {
                Toast.makeText(this, R.string.no_url_available_copy, Toast.LENGTH_SHORT).show()
            }
        }
        
        retryButton.setOnClickListener {
            statusTextView.text = getString(R.string.setup_registering)
            pushHelper.register()
        }
        
        selectDistributorButton.setOnClickListener {
            showDistributorSelectionDialog()
        }
        
        notificationStyleButton.setOnClickListener {
            showNotificationStyleDialog()
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
                statusTextView.text = getString(R.string.setup_success)
                retryButton.visibility = View.GONE
                selectDistributorButton.visibility = View.GONE
                continueButton.isEnabled = true
            } else {
                urlTextView.text = getString(R.string.no_endpoint_check_distributor)
                statusTextView.text = getString(R.string.registration_failed_try_different)
                copyButton.isEnabled = false
                retryButton.visibility = View.VISIBLE
                selectDistributorButton.visibility = View.VISIBLE
                continueButton.isEnabled = false
            }
        }
        
        pushHelper.distributors.observe(this) { distributorList ->
            if (distributorList.isEmpty()) {
                statusTextView.text = getString(R.string.no_distributors_found)
                selectDistributorButton.visibility = View.GONE
            } else {
                val currentDistributor = UnifiedPush.getSavedDistributor(this) ?: ""
                if (currentDistributor.isNotEmpty() && distributorList.contains(currentDistributor)) {
                    statusTextView.text = getString(R.string.using_distributor, currentDistributor)
                } else {
                    statusTextView.text = getString(R.string.found_distributors, distributorList.joinToString())
                }
                selectDistributorButton.visibility = if (distributorList.size > 1) View.VISIBLE else View.GONE
            }
        }
        
        // Start with buttons disabled until we know the status
        continueButton.isEnabled = false
        copyButton.isEnabled = false
        
        pushHelper.register()
    }
    
    private fun showNotificationStyleDialog() {
        val dialogView = layoutInflater.inflate(R.layout.notification_style_dialog, null)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.notificationStyleGroup)
        
        // Set the current style as selected
        val currentStyle = preferencesManager.getNotificationStyle()
        when (currentStyle) {
            PreferencesManager.NOTIFICATION_STYLE_MULTI -> 
                dialogView.findViewById<RadioButton>(R.id.styleMulti).isChecked = true
            PreferencesManager.NOTIFICATION_STYLE_HYBRID -> 
                dialogView.findViewById<RadioButton>(R.id.styleHybrid).isChecked = true
            else -> // Default to multi if unknown style
                dialogView.findViewById<RadioButton>(R.id.styleMulti).isChecked = true
        }
        
        MaterialAlertDialogBuilder(this, R.style.DiscordAlertDialogTheme)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { dialog, which ->
                // Save the selected notification style
                val selectedStyle = when (radioGroup.checkedRadioButtonId) {
                    R.id.styleMulti -> PreferencesManager.NOTIFICATION_STYLE_MULTI
                    R.id.styleHybrid -> PreferencesManager.NOTIFICATION_STYLE_HYBRID
                    else -> PreferencesManager.NOTIFICATION_STYLE_MULTI
                }
                
                preferencesManager.setNotificationStyle(selectedStyle)
                Toast.makeText(this, R.string.notification_style_saved, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun showDistributorSelectionDialog() {
        val distributors = pushHelper.distributors.value ?: return
        if (distributors.isEmpty()) return

        AlertDialog.Builder(this)
            .setTitle(R.string.select_distributor_title)
            .setItems(distributors.toTypedArray()) { dialog, which ->
                val selected = distributors[which]
                statusTextView.text = getString(R.string.switching_to_distributor, selected)

                // Save the selected distributor
                UnifiedPush.saveDistributor(this, selected)
                preferencesManager.setCurrentDistributor(selected)

                // Wait a moment before registering
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    pushHelper.register()
                }, 500)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}