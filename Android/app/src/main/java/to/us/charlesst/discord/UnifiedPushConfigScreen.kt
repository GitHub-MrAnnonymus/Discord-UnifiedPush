package to.us.charlesst.discord

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.unifiedpush.android.connector.UnifiedPush

private const val WEBVIEW_SENTINEL = "__webview__"

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
    private lateinit var notificationTargetButton: Button

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Toast.makeText(this, R.string.notification_permission_granted, Toast.LENGTH_SHORT).show()
        } else {
            showNotificationPermissionDeniedDialog()
        }
    }

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
        notificationTargetButton = findViewById(R.id.notificationTargetButton)

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

        notificationTargetButton.setOnClickListener {
            showNotificationTargetDialog()
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
    
    private data class TargetOption(val label: String, val packageName: String?)

    private fun showNotificationTargetDialog() {
        val options = mutableListOf<TargetOption>()
        options += TargetOption(getString(R.string.target_webview), WEBVIEW_SENTINEL)
        options += TargetOption(getString(R.string.target_system_picker), null)

        val pm = packageManager
        // MATCH_ALL returns every candidate handler, not just the user's currently-
        // chosen default. MATCH_DEFAULT_ONLY collapses to one result when Android sees
        // a verified app link (which Discord and most forks register with autoVerify).
        val seen = mutableSetOf<String>()
        val probes = listOf(
            "https://discord.com/channels/@me",
            "https://discordapp.com/channels/@me",
        )
        for (url in probes) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            for (info in pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)) {
                val pkg = info.activityInfo?.packageName ?: continue
                if (pkg == packageName) continue // skip self
                if (!seen.add(pkg)) continue
                val label = info.loadLabel(pm)?.toString().orEmpty().ifBlank { pkg }
                options += TargetOption("$label ($pkg)", pkg)
            }
        }

        val currentTarget = preferencesManager.getNotificationTarget()
        val currentPkg = preferencesManager.getExternalAppPackage()
        val checkedIndex = when {
            currentTarget == PreferencesManager.NOTIFICATION_TARGET_WEBVIEW -> 0
            currentTarget == PreferencesManager.NOTIFICATION_TARGET_EXTERNAL && currentPkg == null -> 1
            else -> options.indexOfFirst { it.packageName == currentPkg }.coerceAtLeast(0)
        }

        MaterialAlertDialogBuilder(this, R.style.DiscordAlertDialogTheme)
            .setTitle(R.string.choose_notification_target)
            .setSingleChoiceItems(options.map { it.label }.toTypedArray(), checkedIndex) { dialog, which ->
                val chosen = options[which]
                val isWebview = chosen.packageName == WEBVIEW_SENTINEL
                if (isWebview) {
                    preferencesManager.setNotificationTarget(PreferencesManager.NOTIFICATION_TARGET_WEBVIEW)
                    preferencesManager.setExternalAppPackage(null)
                } else {
                    preferencesManager.setNotificationTarget(PreferencesManager.NOTIFICATION_TARGET_EXTERNAL)
                    preferencesManager.setExternalAppPackage(chosen.packageName)
                }
                Toast.makeText(this, getString(R.string.notification_target_saved, chosen.label), Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                // External targets won't go through MainActivity's permission flow, so
                // ask here while the user is still in the config screen.
                if (!isWebview) ensureNotificationPermission()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun ensureNotificationPermission() {
        if (NotificationManagerCompat.from(this).areNotificationsEnabled()) return

        MaterialAlertDialogBuilder(this, R.style.DiscordAlertDialogTheme)
            .setTitle(R.string.notification_permission_title)
            .setMessage(R.string.notification_permission_rationale)
            .setPositiveButton(R.string.grant) { _, _ -> requestNotificationPermissionOrSettings() }
            .setNegativeButton(R.string.not_now, null)
            .show()
    }

    private fun requestNotificationPermissionOrSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        openAppNotificationSettings()
    }

    private fun showNotificationPermissionDeniedDialog() {
        MaterialAlertDialogBuilder(this, R.style.DiscordAlertDialogTheme)
            .setTitle(R.string.notification_permission_title)
            .setMessage(R.string.notification_permission_denied_message)
            .setPositiveButton(R.string.open_settings) { _, _ -> openAppNotificationSettings() }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun openAppNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        startActivity(intent)
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