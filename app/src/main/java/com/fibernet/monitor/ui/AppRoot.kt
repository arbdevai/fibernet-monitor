package com.fibernet.monitor.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fibernet.monitor.data.network.RouterApiClient
import com.fibernet.monitor.data.preferences.AppPreferences
import com.fibernet.monitor.data.repository.RouterRepository
import com.fibernet.monitor.ui.navigation.NavDestination
import com.fibernet.monitor.ui.screens.dashboard.DashboardScreen
import com.fibernet.monitor.ui.screens.dashboard.DashboardViewModel
import com.fibernet.monitor.ui.screens.security.SecurityScreen
import com.fibernet.monitor.ui.screens.security.SecurityViewModel
import com.fibernet.monitor.ui.screens.settings.SettingsScreen
import com.fibernet.monitor.ui.screens.settings.SettingsViewModel
import com.fibernet.monitor.ui.screens.tools.ToolsScreen
import com.fibernet.monitor.ui.screens.tools.ToolsViewModel
import com.fibernet.monitor.ui.screens.wifi.WifiControlScreen
import com.fibernet.monitor.ui.screens.wifi.WifiControlViewModel
import com.fibernet.monitor.ui.theme.CrimsonPrimary

@Composable
fun AppRoot(
    preferences: AppPreferences,
    modifier: Modifier = Modifier
) {
    val repository = remember {
        val apiClient = RouterApiClient()
        RouterRepository(apiClient, preferences)
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val dbHelper = remember { com.fibernet.monitor.data.db.FiberNetDatabaseHelper.getInstance(context) }

    val dashboardViewModel = remember { DashboardViewModel(repository) }
    val securityViewModel = remember { SecurityViewModel(repository) }
    val wifiControlViewModel = remember { WifiControlViewModel(repository) }
    val toolsViewModel = remember { ToolsViewModel(repository, dbHelper) }
    val settingsViewModel = remember { SettingsViewModel(repository) }

    var currentDestination by remember { mutableStateOf(NavDestination.DASHBOARD) }

    // BackHandler bertingkat: kembali ke Dashboard jika sedang di sub-halaman
    BackHandler(enabled = currentDestination != NavDestination.DASHBOARD) {
        currentDestination = NavDestination.DASHBOARD
    }

    // Auto-sync data layar saat berpindah tab (memanfaatkan cache 6s agar tidak membebani router)
    LaunchedEffect(currentDestination) {
        when (currentDestination) {
            NavDestination.DASHBOARD -> dashboardViewModel.loadData(isInitial = false)
            NavDestination.SECURITY -> securityViewModel.loadFilterList()
            NavDestination.WIFI -> wifiControlViewModel.loadConfig()
            NavDestination.TOOLS -> toolsViewModel.loadStatus()
            NavDestination.SETTINGS -> {}
        }
    }

    // Cek update aplikasi secara otomatis di background saat dibuka
    LaunchedEffect(Unit) {
        settingsViewModel.checkForUpdates(context)
    }

    val settingsState by settingsViewModel.uiState.collectAsState()
    val hasUpdate = settingsState.updateState is com.fibernet.monitor.data.model.UpdateDownloadState.Available ||
                    settingsState.updateState is com.fibernet.monitor.data.model.UpdateDownloadState.ReadyToInstall

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavDestination.values().forEach { destination ->
                    val isSelected = currentDestination == destination
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentDestination = destination },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (destination == NavDestination.SETTINGS && hasUpdate) {
                                        Badge(containerColor = CrimsonPrimary)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = destination.title
                                )
                            }
                        },
                        label = { Text(destination.title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = CrimsonPrimary,
                            indicatorColor = CrimsonPrimary,
                            unselectedIconColor = MaterialTheme.colorScheme.outline,
                            unselectedTextColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = currentDestination,
            transitionSpec = {
                val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (slideInHorizontally { width -> direction * (width / 3) } + fadeIn()) togetherWith
                (slideOutHorizontally { width -> -direction * (width / 3) } + fadeOut())
            },
            label = "ScreenTransition",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { destination ->
            when (destination) {
                NavDestination.DASHBOARD -> DashboardScreen(viewModel = dashboardViewModel)
                NavDestination.SECURITY -> SecurityScreen(viewModel = securityViewModel)
                NavDestination.WIFI -> WifiControlScreen(viewModel = wifiControlViewModel)
                NavDestination.TOOLS -> ToolsScreen(viewModel = toolsViewModel)
                NavDestination.SETTINGS -> SettingsScreen(viewModel = settingsViewModel)
            }
        }
    }
}
