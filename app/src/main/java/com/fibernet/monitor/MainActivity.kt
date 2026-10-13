package com.fibernet.monitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fibernet.monitor.data.preferences.AppPreferences
import com.fibernet.monitor.ui.AppRoot
import com.fibernet.monitor.ui.theme.FiberNetTheme

class MainActivity : ComponentActivity() {

    private lateinit var preferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        preferences = AppPreferences(applicationContext)

        setContent {
            FiberNetTheme {
                AppRoot(preferences = preferences)
            }
        }
    }
}
