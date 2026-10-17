package com.fibernet.monitor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.sp
import com.fibernet.monitor.data.model.WifiSignalSample
import com.fibernet.monitor.ui.theme.*

@Composable
fun WifiSignalWaveform(
    samples: List<WifiSignalSample>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Live Coverage Waveform (Real-time Walk Test)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            val latest = samples.lastOrNull()?.rssiDbm ?: -100
            Text(
                text = "$latest dBm",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                color = when {
                    latest >= -60 -> SignalExcellent
                    latest >= -72 -> SignalGood
                    latest >= -82 -> SignalFair
                    else -> SignalWeak
                }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Draw background horizontal dBm reference lines (-50, -70, -90 dBm)
                val dbmLines = listOf(-50, -70, -90)
                dbmLines.forEach { dbm ->
                    // Normalize: -40 dBm -> top (0), -100 dBm -> bottom (h)
                    val normY = ((dbm - (-40f)) / (-100f - (-40f))) * h
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.2f),
                        start = Offset(0f, normY),
                        end = Offset(w, normY),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                if (samples.size >= 2) {
                    val minDbm = -95f
                    val maxDbm = -40f
                    val stepX = w / (samples.size - 1).coerceAtLeast(1)

                    val strokePath = Path()
                    val fillPath = Path()

                    samples.forEachIndexed { i, sample ->
                        val x = i * stepX
                        val clampedDbm = sample.rssiDbm.toFloat().coerceIn(minDbm, maxDbm)
                        val y = ((maxDbm - clampedDbm) / (maxDbm - minDbm)) * h

                        if (i == 0) {
                            strokePath.moveTo(x, y)
                            fillPath.moveTo(x, h)
                            fillPath.lineTo(x, y)
                        } else {
                            strokePath.lineTo(x, y)
                            fillPath.lineTo(x, y)
                        }
                    }

                    fillPath.lineTo(w, h)
                    fillPath.close()

                    // Draw gradient fill under wave
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                CrimsonPrimary.copy(alpha = 0.28f),
                                CrimsonPrimary.copy(alpha = 0.02f)
                            )
                        )
                    )

                    // Draw smooth stroke line
                    drawPath(
                        path = strokePath,
                        color = CrimsonPrimary,
                        style = Stroke(
                            width = 3.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    )

                    // Draw glowing pulse dot at the latest point
                    val lastX = (samples.size - 1) * stepX
                    val lastDbm = samples.last().rssiDbm.toFloat().coerceIn(minDbm, maxDbm)
                    val lastY = ((maxDbm - lastDbm) / (maxDbm - minDbm)) * h

                    drawCircle(
                        color = CrimsonPrimary.copy(alpha = 0.3f),
                        radius = 8.dp.toPx(),
                        center = Offset(lastX, lastY)
                    )
                    drawCircle(
                        color = CrimsonPrimary,
                        radius = 4.dp.toPx(),
                        center = Offset(lastX, lastY)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("← Past 30 Seconds", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Text("Live Now →", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}
