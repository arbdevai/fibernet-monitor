package com.fibernet.monitor.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "fibernet_settings")

data class RouterSettings(
    val routerIp: String = "192.168.1.1",
    val username: String = "admin",
    val password: String = "%0|F?H@f!berhO3e",
    val refreshIntervalSeconds: Int = 10,
    val notifyNewDevice: Boolean = true
)

class AppPreferences(private val context: Context) {

    private object Keys {
        val ROUTER_IP = stringPreferencesKey("router_ip")
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password")
        val REFRESH_INTERVAL = intPreferencesKey("refresh_interval")
        val NOTIFY_NEW_DEVICE = booleanPreferencesKey("notify_new_device")
    }

    val settingsFlow: Flow<RouterSettings> = context.dataStore.data.map { prefs ->
        RouterSettings(
            routerIp = prefs[Keys.ROUTER_IP] ?: "192.168.1.1",
            username = prefs[Keys.USERNAME] ?: "admin",
            password = prefs[Keys.PASSWORD] ?: "%0|F?H@f!berhO3e",
            refreshIntervalSeconds = prefs[Keys.REFRESH_INTERVAL] ?: 10,
            notifyNewDevice = prefs[Keys.NOTIFY_NEW_DEVICE] ?: true
        )
    }

    suspend fun updateSettings(
        routerIp: String,
        username: String,
        password: String,
        refreshIntervalSeconds: Int,
        notifyNewDevice: Boolean
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ROUTER_IP] = routerIp.trim()
            prefs[Keys.USERNAME] = username.trim()
            prefs[Keys.PASSWORD] = password
            prefs[Keys.REFRESH_INTERVAL] = refreshIntervalSeconds
            prefs[Keys.NOTIFY_NEW_DEVICE] = notifyNewDevice
        }
    }
}
