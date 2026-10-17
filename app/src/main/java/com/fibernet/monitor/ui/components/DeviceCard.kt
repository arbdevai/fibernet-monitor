package com.fibernet.monitor.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fibernet.monitor.data.model.DeviceItem
import com.fibernet.monitor.ui.theme.*

@Composable
fun DeviceCard(
    device: DeviceItem,
    onBlockClick: (DeviceItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val bandColor = when {
        device.accessType.contains("5G", ignoreCase = true) -> Band5GHz
        device.accessType.contains("2.4", ignoreCase = true) -> Band24GHz
        else -> BandLan
    }

    val icon = when {
        device.hostName.contains("iphone", ignoreCase = true) ||
        device.hostName.contains("oppo", ignoreCase = true) ||
        device.hostName.contains("samsung", ignoreCase = true) ||
        device.hostName.contains("redmi", ignoreCase = true) ||
        device.hostName.contains("vivo", ignoreCase = true) ||
        device.hostName.contains("xiaomi", ignoreCase = true) ||
        device.hostName.startsWith("A25", ignoreCase = true) ||
        device.hostName.startsWith("A34", ignoreCase = true) ||
        device.hostName.startsWith("M20", ignoreCase = true) -> Icons.Rounded.Smartphone
        device.deviceType.contains("computer", ignoreCase = true) -> Icons.Rounded.Laptop
        else -> Icons.Rounded.Devices
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Baris 1: Icon, Nama Perangkat, Badge Frekuensi
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(bandColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = bandColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        // Online indicator dot
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (device.isOnline) SignalExcellent else MaterialTheme.colorScheme.outline)
                        )
                        Text(
                            text = if (device.isOnline) "Online" else "Offline",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (device.isOnline) SignalExcellent else MaterialTheme.colorScheme.outline
                        )
                    }
                }

                // Band Badge
                Surface(
                    color = bandColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (device.accessType.isNotBlank()) device.accessType else "LAN",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = bandColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Divider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))

            // Row 2: IP Address, MAC Address (click to copy)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("IP", device.ipAddress))
                            Toast.makeText(context, "IP copied: ${device.ipAddress}", Toast.LENGTH_SHORT).show()
                        }
                ) {
                    Text(
                        text = "IP ADDRESS",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = device.ipAddress.ifBlank { "-" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("MAC", device.macAddress))
                            Toast.makeText(context, "MAC copied: ${device.macAddress}", Toast.LENGTH_SHORT).show()
                        }
                ) {
                    Text(
                        text = "MAC ADDRESS",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = device.macAddress.ifBlank { "-" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Baris 3: Sinyal & Kecepatan Trafik & Tombol Blokir
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Sinyal Bar
                SignalIndicator(rssi = device.rssi)

                // Traffic speed info
                if (device.upSpeedKbps > 0 || device.downSpeedKbps > 0) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "↑ ${device.upSpeedKbps} KB/s",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = "↓ ${device.downSpeedKbps} KB/s",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                // Tombol Blokir Aksi Cepat
                OutlinedButton(
                    onClick = { onBlockClick(device) },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = CrimsonPrimary
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(CrimsonPrimary.copy(alpha = 0.5f))
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Block,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Block",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
