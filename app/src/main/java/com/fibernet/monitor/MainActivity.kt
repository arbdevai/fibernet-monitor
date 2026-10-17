package com.fibernet.monitor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.fibernet.monitor.data.preferences.AppPreferences
import com.fibernet.monitor.data.worker.RouterAlertWorker
import com.fibernet.monitor.ui.AppRoot
import com.fibernet.monitor.ui.theme.FiberNetTheme

class MainActivity : ComponentActivity() {

    private lateinit var preferences: AppPreferences

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // Permission result handled
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        preferences = AppPreferences(applicationContext)

        // Jadwalkan pemantauan background & early warning
        RouterAlertWorker.schedule(applicationContext)

        // Cek izin notifikasi untuk Android 13+ (TIRAMISU / Android 14)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            FiberNetTheme {
                AppRoot(preferences = preferences)
            }
        }
    }
}
