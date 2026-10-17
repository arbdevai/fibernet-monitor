package com.fibernet.monitor.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.fibernet.monitor.MainActivity
import com.fibernet.monitor.R
import com.fibernet.monitor.data.db.FiberNetDatabaseHelper
import com.fibernet.monitor.data.network.RouterApiClient
import com.fibernet.monitor.data.preferences.AppPreferences
import com.fibernet.monitor.data.repository.RouterRepository
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class RouterAlertWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "router_alert_worker"
        const val CHANNEL_ID = "fibernet_alerts"
        const val CHANNEL_NAME = "Peringatan Router & Jaringan"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<RouterAlertWorker>(
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
        }
    }

    override suspend fun doWork(): Result {
        val prefs = AppPreferences(context)
        val settings = prefs.settingsFlow.first()
        val db = FiberNetDatabaseHelper.getInstance(context)
        val repository = RouterRepository(RouterApiClient(), prefs)

        val statusRes = repository.getRouterStatus()
        statusRes.getOrNull()?.let { status ->
            val base = status.baseInfo
            val rxPower = base.rxPowerDbm
            val txPower = base.txPowerDbm
            val temp = base.temperatureCelsius

            // Simpan riwayat optik ke DB lokal
            db.insertOpticalRecord(rxPower, txPower, temp)

            // Peringatan Redaman Optik Drop (< -27.0 dBm)
            if (rxPower < -27.0 && rxPower > -50.0) {
                showNotification(
                    id = 101,
                    title = "Peringatan: Redaman Optik Drop",
                    message = "Redaman Rx turun menjadi ${rxPower} dBm. Periksa apakah kabel patch cord fiber tertekuk atau kotor."
                )
            }

            // Peringatan Suhu Laser Overheat (> 50.0 C)
            if (temp > 50.0) {
                showNotification(
                    id = 102,
                    title = "Peringatan: Suhu Router Panas",
                    message = "Suhu modul optik mencapai ${temp}°C. Pastikan sirkulasi udara di sekitar router memadai."
                )
            }

            // Periksa Status Sambungan Internet (WAN)
            val internetWan = status.internetWan
            if (internetWan != null && !internetWan.isConnected) {
                db.insertDisconnection(
                    timestamp = System.currentTimeMillis(),
                    durationSeconds = 0,
                    reason = "Koneksi PPPoE terputus: ${internetWan.name}"
                )
                showNotification(
                    id = 103,
                    title = "Internet Terputus (Disconnected)",
                    message = "Sambungan WAN ${internetWan.name} offline. Router kehilangan koneksi ke server ISP."
                )
            }
        }

        // Periksa Perangkat Baru yang Terhubung
        if (settings.notifyNewDevice) {
            val devicesRes = repository.getConnectedDevices()
            devicesRes.getOrNull()?.let { devices ->
                for (dev in devices) {
                    val isNew = db.upsertKnownDevice(dev.macAddress, dev.ipAddress, dev.hostName)
                    if (isNew) {
                        val displayName = dev.hostName.ifBlank { dev.macAddress }
                        showNotification(
                            id = (1000..9999).random(),
                            title = "Perangkat Baru Terdeteksi",
                            message = "Perangkat '$displayName' (${dev.ipAddress}) baru saja terhubung ke jaringan Wi-Fi Anda."
                        )
                    }
                }
            }
        }

        return Result.success()
    }

    private fun showNotification(id: Int, title: String, message: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi status darurat dan perangkat baru FiberNet"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_router)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(soundUri)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(id, notification)
    }
}
