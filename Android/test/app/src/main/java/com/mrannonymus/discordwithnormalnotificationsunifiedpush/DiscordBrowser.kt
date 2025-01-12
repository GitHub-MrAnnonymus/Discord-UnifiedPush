package com.mrannonymus.discordwithnormalnotificationsunifiedpush

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun DiscordBrowser(modifier: Modifier = Modifier) {
    var useCustomTabs by remember { mutableStateOf(true) }
    val context = LocalContext.current

    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Choose how to open Discord:",
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        ElevatedButton(
            onClick = { openDiscord(context, useCustomTabs) },
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Text(if (useCustomTabs) "Open in App Browser" else "Open in System Browser")
        }
        
        TextButton(
            onClick = { useCustomTabs = !useCustomTabs }
        ) {
            Text("Switch to ${if (useCustomTabs) "System Browser" else "App Browser"}")
        }
    }
}

private fun openDiscord(context: Context, useCustomTabs: Boolean) {
    val url = "https://discord.com/app"
    if (useCustomTabs) {
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(context, Uri.parse(url))
    } else {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }
} 