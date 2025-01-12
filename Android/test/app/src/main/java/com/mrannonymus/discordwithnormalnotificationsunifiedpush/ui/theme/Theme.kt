package com.mrannonymus.discordwithnormalnotificationsunifiedpush.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = DiscordBlue,
    onPrimary = DiscordText,
    secondary = DiscordLightBlue,
    onSecondary = DiscordText,
    tertiary = DiscordDarkBlue,
    background = DiscordGrey,
    surface = DiscordLightGrey,
    onBackground = DiscordText,
    onSurface = DiscordText
)

@Composable
fun DiscordWithNormalNotificationsUnifiedPushTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = DarkColorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}