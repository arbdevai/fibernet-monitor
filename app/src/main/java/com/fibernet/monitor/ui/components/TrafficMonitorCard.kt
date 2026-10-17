package com.fibernet.monitor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fibernet.monitor.data.model.BaseInfoResponse
import com.fibernet.monitor.ui.theme.CrimsonPrimary
import com.fibernet.monitor.ui.theme.SignalExcellent
import kotlin.math.max

@Composable
fun TrafficMonitorCard(
    downSpeedKbps: Long,
    upSpeedKbps: Long,
    downHistory: List<Float>,
    upHistory: List<Float>,
    baseInfo: BaseInfoResponse,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SignalExcellent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Speed,
                            contentDescription = null,
                            tint = SignalExcellent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Trafik & Akumulasi Kuota (Live)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Aktivitas Bandwidth GPON & Riwayat Data",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                Surface(
                    color = SignalExcellent.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Real-time",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = SignalExcellent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Kecepatan Saat Ini
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SpeedBadge(
                    label = "KECEPATAN UNDUH",
                    speedKbps = downSpeedKbps,
                    color = SignalExcellent,
                    icon = Icons.Rounded.ArrowDownward,
                    modifier = Modifier.weight(1f)
                )
                SpeedBadge(
                    label = "KECEPATAN UNGGAH",
                    speedKbps = upSpeedKbps,
                    color = CrimsonPrimary,
                    icon = Icons.Rounded.ArrowUpward,
                    modifier = Modifier.weight(1f)
                )
            }

            // Grafik Kurva Real-Time
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                RealtimeSplineGraph(
                    dataPoints = downHistory,
                    lineColor = SignalExcellent,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Divider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

            // Total Riwayat Kuota dalam GB (Ramah Orang Awam)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DataUsage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Total Riwayat Penggunaan Data (Bulan Ini)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuotaBox(
                        title = "Total Unduhan",
                        gbValue = baseInfo.ponDownloadedGb,
                        color = SignalExcellent,
                        modifier = Modifier.weight(1f)
                    )
                    QuotaBox(
                        title = "Total Unggahan",
                        gbValue = baseInfo.ponUploadedGb,
                        color = CrimsonPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    QuotaBox(
                        title = "Total Kuota",
                        gbValue = baseInfo.ponDownloadedGb + baseInfo.ponUploadedGb,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedBadge(
    label: String,
    speedKbps: Long,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    val speedText = if (speedKbps >= 1024L) {
        "%.1f MB/s".format(speedKbps / 1024.0)
    } else {
        "$speedKbps KB/s"
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.08f)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = speedText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
        }
    }
}

@Composable
private fun QuotaBox(
    title: String,
    gbValue: Double,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "%.2f GB".format(gbValue),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun RealtimeSplineGraph(
    dataPoints: List<Float>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (dataPoints.isEmpty()) return@Canvas

        val maxVal = max(dataPoints.maxOrNull() ?: 100f, 100f)
        val w = size.width
        val h = size.height

        // Garis pemandu horizontal tipis
        val guideY1 = h * 0.25f
        val guideY2 = h * 0.75f
        drawLine(
            color = Color.Gray.copy(alpha = 0.15f),
            start = Offset(0f, guideY1),
            end = Offset(w, guideY1),
            strokeWidth = 1f
        )
        drawLine(
            color = Color.Gray.copy(alpha = 0.15f),
            start = Offset(0f, guideY2),
            end = Offset(w, guideY2),
            strokeWidth = 1f
        )

        val stepX = if (dataPoints.size > 1) w / (dataPoints.size - 1) else w

        val path = Path()
        val fillPath = Path()

        for (i in dataPoints.indices) {
            val x = i * stepX
            val normalized = (dataPoints[i] / maxVal).coerceIn(0f, 1f)
            val y = h - (normalized * (h - 8.dp.toPx())) - 4.dp.toPx()

            if (i == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, h)
                fillPath.lineTo(x, y)
            } else {
                val prevX = (i - 1) * stepX
                val prevNormalized = (dataPoints[i - 1] / maxVal).coerceIn(0f, 1f)
                val prevY = h - (prevNormalized * (h - 8.dp.toPx())) - 4.dp.toPx()

                val cx = (prevX + x) / 2f
                path.cubicTo(cx, prevY, cx, y, x, y)
                fillPath.cubicTo(cx, prevY, cx, y, x, y)
            }

            if (i == dataPoints.lastIndex) {
                fillPath.lineTo(x, h)
                fillPath.close()
            }
        }

        // Draw gradient under curve
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.35f), Color.Transparent),
                startY = 0f,
                endY = h
            )
        )

        // Draw main line
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
