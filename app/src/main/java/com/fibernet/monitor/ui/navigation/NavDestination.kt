package com.fibernet.monitor.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class NavDestination(
    val title: String,
    val icon: ImageVector
) {
    DASHBOARD("Beranda", Icons.Rounded.Wifi),
    SECURITY("Blokir", Icons.Rounded.Security),
    WIFI("WiFi", Icons.Rounded.SettingsInputAntenna),
    TOOLS("Alat", Icons.Rounded.Build),
    SETTINGS("Setelan", Icons.Rounded.Tune)
}
