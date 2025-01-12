package com.mrannonymus.discordwithnormalnotificationsunifiedpush

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun UnifiedPushConfigScreen(
    pushHelper: UnifiedPushHelper,
    onConfigured: () -> Unit
) {
    var showDistributorPicker by remember { mutableStateOf(false) }
    val endpoint = remember { pushHelper.endpoint }
    val distributors = remember { pushHelper.distributors }
    val context = LocalContext.current
    var showCopySuccess by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "UnifiedPush Configuration",
            fontSize = 24.sp,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        if (!showDistributorPicker && endpoint.value == null) {
            Text(
                text = "Choose how you want to receive notifications",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            ElevatedButton(
                onClick = { 
                    pushHelper.register()
                    if (distributors.value.size > 1) {
                        showDistributorPicker = true
                    }
                }
            ) {
                Text("Configure Notifications")
            }
        } else if (showDistributorPicker && endpoint.value == null) {
            CircularProgressIndicator(
                modifier = Modifier.padding(16.dp)
            )
            Text(
                text = "Waiting for distributor selection...",
                modifier = Modifier.padding(16.dp)
            )
            OutlinedButton(
                onClick = { showDistributorPicker = false },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Cancel")
            }
        }

        // Show endpoint and buttons when available
        endpoint.value?.let { url ->
            Text(
                text = "Configuration Successful!",
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                text = "Your UnifiedPush endpoint:",
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = url,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 16.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("UnifiedPush Endpoint", url)
                        clipboard.setPrimaryClip(clip)
                        showCopySuccess = true
                    }
                ) {
                    Text("Copy Endpoint")
                }
                ElevatedButton(onClick = onConfigured) {
                    Text("Continue")
                }
            }
            if (showCopySuccess) {
                Text(
                    text = "Endpoint copied!",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
} 