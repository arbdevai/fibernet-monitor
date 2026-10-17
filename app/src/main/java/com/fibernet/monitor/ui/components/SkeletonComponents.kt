package com.fibernet.monitor.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Reusable shimmer modifier for skeleton screens in Jetpack Compose.
 * Creates a smooth, GPU-accelerated gradient sweep without external dependencies.
 */
fun Modifier.shimmerEffect(
    shape: Shape = RoundedCornerShape(8.dp),
    durationMillis: Int = 1100
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmerTransition")
    val translateAnimation by transition.animateFloat(
        initialValue = -500f,
        targetValue = 1300f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    val isDark = MaterialTheme.colorScheme.surface.run { (red * 0.299 + green * 0.587 + blue * 0.114) < 0.5 }
    val baseColor = if (isDark) Color(0xFF2B2020) else Color(0xFFF1E6E5)
    val highlightColor = if (isDark) Color(0xFF3F3030) else Color(0xFFFFFFFF)

    val brush = Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset(x = translateAnimation - 350f, y = translateAnimation - 350f),
        end = Offset(x = translateAnimation, y = translateAnimation)
    )

    clip(shape).background(brush)
}

@Composable
fun SkeletonBox(
    modifier: Modifier,
    shape: Shape = RoundedCornerShape(8.dp)
) {
    Box(modifier = modifier.shimmerEffect(shape))
}

@Composable
fun DashboardSkeleton(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Router Status Banner Skeleton
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SkeletonBox(modifier = Modifier.size(42.dp), shape = RoundedCornerShape(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SkeletonBox(modifier = Modifier.size(width = 130.dp, height = 18.dp))
                            SkeletonBox(modifier = Modifier.size(width = 90.dp, height = 12.dp))
                        }
                    }
                    SkeletonBox(modifier = Modifier.size(width = 75.dp, height = 26.dp), shape = RoundedCornerShape(13.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SkeletonBox(modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(12.dp))
                    SkeletonBox(modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(12.dp))
                }
            }
        }

        // 2. Traffic Monitor Card Skeleton
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SkeletonBox(modifier = Modifier.size(width = 120.dp, height = 16.dp))
                    SkeletonBox(modifier = Modifier.size(width = 70.dp, height = 14.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SkeletonBox(modifier = Modifier.weight(1f).height(64.dp), shape = RoundedCornerShape(12.dp))
                    SkeletonBox(modifier = Modifier.weight(1f).height(64.dp), shape = RoundedCornerShape(12.dp))
                }
                SkeletonBox(modifier = Modifier.fillMaxWidth().height(80.dp), shape = RoundedCornerShape(12.dp))
            }
        }

        // 3. Optical Status Card Skeleton
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SkeletonBox(modifier = Modifier.size(width = 150.dp, height = 16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SkeletonBox(modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(10.dp))
                    SkeletonBox(modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(10.dp))
                    SkeletonBox(modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(10.dp))
                }
            }
        }

        // 4. Device Cards Skeleton (3 items)
        repeat(3) {
            DeviceCardSkeleton()
        }
    }
}

@Composable
fun DeviceCardSkeleton(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SkeletonBox(modifier = Modifier.size(42.dp), shape = RoundedCornerShape(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SkeletonBox(modifier = Modifier.size(width = 140.dp, height = 16.dp))
                SkeletonBox(modifier = Modifier.size(width = 180.dp, height = 12.dp))
            }
            SkeletonBox(modifier = Modifier.size(width = 50.dp, height = 24.dp), shape = RoundedCornerShape(8.dp))
        }
    }
}

@Composable
fun WifiControlSkeleton(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Radio Card 2.4 GHz Skeleton
        WifiRadioCardSkeleton()
        // Radio Card 5 GHz Skeleton
        WifiRadioCardSkeleton()
        // WPS Card Skeleton
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SkeletonBox(modifier = Modifier.size(42.dp), shape = RoundedCornerShape(10.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SkeletonBox(modifier = Modifier.size(width = 110.dp, height = 16.dp))
                    SkeletonBox(modifier = Modifier.size(width = 170.dp, height = 12.dp))
                }
                SkeletonBox(modifier = Modifier.size(width = 80.dp, height = 36.dp), shape = RoundedCornerShape(10.dp))
            }
        }
    }
}

@Composable
fun WifiRadioCardSkeleton(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SkeletonBox(modifier = Modifier.size(38.dp), shape = RoundedCornerShape(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SkeletonBox(modifier = Modifier.size(width = 110.dp, height = 16.dp))
                        SkeletonBox(modifier = Modifier.size(width = 140.dp, height = 12.dp))
                    }
                }
                SkeletonBox(modifier = Modifier.size(width = 46.dp, height = 24.dp), shape = RoundedCornerShape(12.dp))
            }

            SkeletonBox(modifier = Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(12.dp))
            SkeletonBox(modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp))
        }
    }
}

@Composable
fun SecuritySkeleton(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Firewall Status Card Skeleton
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SkeletonBox(modifier = Modifier.size(42.dp), shape = RoundedCornerShape(10.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SkeletonBox(modifier = Modifier.size(width = 130.dp, height = 16.dp))
                    SkeletonBox(modifier = Modifier.size(width = 190.dp, height = 12.dp))
                }
                SkeletonBox(modifier = Modifier.size(width = 70.dp, height = 26.dp), shape = RoundedCornerShape(13.dp))
            }
        }

        // MAC Filter List Header Skeleton
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SkeletonBox(modifier = Modifier.size(width = 150.dp, height = 18.dp))
            SkeletonBox(modifier = Modifier.size(width = 80.dp, height = 32.dp), shape = RoundedCornerShape(8.dp))
        }

        repeat(3) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SkeletonBox(modifier = Modifier.size(width = 120.dp, height = 15.dp))
                        SkeletonBox(modifier = Modifier.size(width = 160.dp, height = 12.dp))
                    }
                    SkeletonBox(modifier = Modifier.size(34.dp), shape = CircleShape)
                }
            }
        }
    }
}
