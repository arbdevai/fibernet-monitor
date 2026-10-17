package com.fibernet.monitor.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DisconnectionLog(
    val id: Long = 0,
    val timestamp: Long,
    val durationSeconds: Long,
    val reason: String
)

data class OpticalRecord(
    val id: Long = 0,
    val timestamp: Long,
    val rxPower: Double,
    val txPower: Double,
    val laserTemp: Double
)

data class KnownDevice(
    val macAddress: String,
    val customName: String?,
    val firstSeen: Long,
    val lastSeen: Long,
    val ipAddress: String,
    val hostname: String
)

class FiberNetDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    companion object {
        const val DATABASE_NAME = "fibernet_monitor.db"
        const val DATABASE_VERSION = 1

        @Volatile
        private var INSTANCE: FiberNetDatabaseHelper? = null

        fun getInstance(context: Context): FiberNetDatabaseHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FiberNetDatabaseHelper(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE disconnection_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp INTEGER NOT NULL,
                duration_seconds INTEGER NOT NULL,
                reason TEXT NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE optical_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp INTEGER NOT NULL,
                rx_power REAL NOT NULL,
                tx_power REAL NOT NULL,
                laser_temp REAL NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE known_devices (
                mac_address TEXT PRIMARY KEY,
                custom_name TEXT,
                first_seen INTEGER NOT NULL,
                last_seen INTEGER NOT NULL,
                ip_address TEXT NOT NULL,
                hostname TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS disconnection_logs")
        db.execSQL("DROP TABLE IF EXISTS optical_history")
        db.execSQL("DROP TABLE IF EXISTS known_devices")
        onCreate(db)
    }

    suspend fun insertDisconnection(timestamp: Long, durationSeconds: Long, reason: String): Long =
        withContext(Dispatchers.IO) {
            val db = writableDatabase
            val values = ContentValues().apply {
                put("timestamp", timestamp)
                put("duration_seconds", durationSeconds)
                put("reason", reason)
            }
            db.insert("disconnection_logs", null, values)
        }

    suspend fun getDisconnectionLogs(limit: Int = 30): List<DisconnectionLog> =
        withContext(Dispatchers.IO) {
            val db = readableDatabase
            val list = mutableListOf<DisconnectionLog>()
            db.query(
                "disconnection_logs",
                null,
                null,
                null,
                null,
                null,
                "timestamp DESC",
                limit.toString()
            ).use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow("id")
                val timeIdx = cursor.getColumnIndexOrThrow("timestamp")
                val durIdx = cursor.getColumnIndexOrThrow("duration_seconds")
                val reasonIdx = cursor.getColumnIndexOrThrow("reason")
                while (cursor.moveToNext()) {
                    list.add(
                        DisconnectionLog(
                            id = cursor.getLong(idIdx),
                            timestamp = cursor.getLong(timeIdx),
                            durationSeconds = cursor.getLong(durIdx),
                            reason = cursor.getString(reasonIdx)
                        )
                    )
                }
            }
            list
        }

    suspend fun insertOpticalRecord(rxPower: Double, txPower: Double, laserTemp: Double): Long =
        withContext(Dispatchers.IO) {
            val db = writableDatabase
            val values = ContentValues().apply {
                put("timestamp", System.currentTimeMillis())
                put("rx_power", rxPower)
                put("tx_power", txPower)
                put("laser_temp", laserTemp)
            }
            db.insert("optical_history", null, values)
        }

    suspend fun getOpticalHistory(limit: Int = 50): List<OpticalRecord> =
        withContext(Dispatchers.IO) {
            val db = readableDatabase
            val list = mutableListOf<OpticalRecord>()
            db.query(
                "optical_history",
                null,
                null,
                null,
                null,
                null,
                "timestamp DESC",
                limit.toString()
            ).use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow("id")
                val timeIdx = cursor.getColumnIndexOrThrow("timestamp")
                val rxIdx = cursor.getColumnIndexOrThrow("rx_power")
                val txIdx = cursor.getColumnIndexOrThrow("tx_power")
                val tempIdx = cursor.getColumnIndexOrThrow("laser_temp")
                while (cursor.moveToNext()) {
                    list.add(
                        OpticalRecord(
                            id = cursor.getLong(idIdx),
                            timestamp = cursor.getLong(timeIdx),
                            rxPower = cursor.getDouble(rxIdx),
                            txPower = cursor.getDouble(txIdx),
                            laserTemp = cursor.getDouble(tempIdx)
                        )
                    )
                }
            }
            list
        }

    suspend fun upsertKnownDevice(mac: String, ip: String, hostname: String): Boolean =
        withContext(Dispatchers.IO) {
            val db = writableDatabase
            val now = System.currentTimeMillis()
            var isNew = false

            db.query(
                "known_devices",
                arrayOf("mac_address", "custom_name"),
                "mac_address = ?",
                arrayOf(mac),
                null,
                null,
                null
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    val values = ContentValues().apply {
                        put("last_seen", now)
                        put("ip_address", ip)
                        if (hostname.isNotBlank()) put("hostname", hostname)
                    }
                    db.update("known_devices", values, "mac_address = ?", arrayOf(mac))
                } else {
                    isNew = true
                    val values = ContentValues().apply {
                        put("mac_address", mac)
                        put("custom_name", null as String?)
                        put("first_seen", now)
                        put("last_seen", now)
                        put("ip_address", ip)
                        put("hostname", hostname)
                    }
                    db.insert("known_devices", null, values)
                }
            }
            isNew
        }

    suspend fun setCustomDeviceName(mac: String, customName: String) =
        withContext(Dispatchers.IO) {
            val db = writableDatabase
            val values = ContentValues().apply {
                put("custom_name", customName.trim())
            }
            db.update("known_devices", values, "mac_address = ?", arrayOf(mac))
        }

    suspend fun getKnownDevices(): List<KnownDevice> =
        withContext(Dispatchers.IO) {
            val db = readableDatabase
            val list = mutableListOf<KnownDevice>()
            db.query(
                "known_devices",
                null,
                null,
                null,
                null,
                null,
                "last_seen DESC"
            ).use { cursor ->
                val macIdx = cursor.getColumnIndexOrThrow("mac_address")
                val nameIdx = cursor.getColumnIndexOrThrow("custom_name")
                val firstIdx = cursor.getColumnIndexOrThrow("first_seen")
                val lastIdx = cursor.getColumnIndexOrThrow("last_seen")
                val ipIdx = cursor.getColumnIndexOrThrow("ip_address")
                val hostIdx = cursor.getColumnIndexOrThrow("hostname")
                while (cursor.moveToNext()) {
                    list.add(
                        KnownDevice(
                            macAddress = cursor.getString(macIdx),
                            customName = if (cursor.isNull(nameIdx)) null else cursor.getString(nameIdx),
                            firstSeen = cursor.getLong(firstIdx),
                            lastSeen = cursor.getLong(lastIdx),
                            ipAddress = cursor.getString(ipIdx),
                            hostname = cursor.getString(hostIdx)
                        )
                    )
                }
            }
            list
        }
}
