package com.fibernet.monitor.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class NavDestination(
    val title: String,
    val icon: ImageVector
) {
    DASHBOARD("Dashboard", Icons.Rounded.Wifi),
    SECURITY("Security", Icons.Rounded.Security),
    WIFI("Wi-Fi", Icons.Rounded.SettingsInputAntenna),
    TOOLS("Tools", Icons.Rounded.Build),
    SETTINGS("Settings", Icons.Rounded.Tune)
}
