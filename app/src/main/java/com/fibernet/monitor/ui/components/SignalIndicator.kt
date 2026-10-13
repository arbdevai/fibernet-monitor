package com.fibernet.monitor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fibernet.monitor.ui.theme.*

@Composable
fun SignalIndicator(
    rssi: Int,
    modifier: Modifier = Modifier
) {
    // RSSI ranges: -50 dBm (sangat kuat) sampai -95 dBm (lemah)
    val (level, color, label) = when {
        rssi == 0 -> Triple(0, LightOutline, "LAN")
        rssi >= -65 -> Triple(4, SignalExcellent, "Sangat Kuat")
        rssi >= -75 -> Triple(3, SignalGood, "Kuat")
        rssi >= -85 -> Triple(2, SignalFair, "Cukup")
        else -> Triple(1, SignalWeak, "Lemah")
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (rssi != 0) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.height(14.dp)
            ) {
                for (bar in 1..4) {
                    val barHeight = (bar * 3.5).dp
                    val isFilled = bar <= level
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(barHeight)
                            .clip(RoundedCornerShape(1.dp))
                            .background(if (isFilled) color else color.copy(alpha = 0.25f))
                    )
                }
            }
            Text(
                text = "$rssi dBm",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = color
            )
        } else {
            Text(
                text = "LAN Kabel",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = BandLan
            )
        }
    }
}
