package com.fibernet.monitor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.fibernet.monitor.ui.theme.Band24GHz
import com.fibernet.monitor.ui.theme.Band5GHz
import com.fibernet.monitor.ui.theme.CrimsonPrimary

@Composable
fun CreateEditSsidDialog(
    initialIs5G: Boolean = false,
    initialIndex: Int = 1,
    initialSsid: String = "",
    initialPassword: String = "",
    initialEnabled: Boolean = true,
    initialHidden: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (is5G: Boolean, wifiIndex: Int, ssid: String, password: String, enabled: Boolean, hideBroadcast: Boolean) -> Unit
) {
    var is5G by remember { mutableStateOf(initialIs5G) }
    var slotIndex by remember { mutableStateOf(initialIndex) }
    var ssidInput by remember { mutableStateOf(initialSsid) }
    var passwordInput by remember { mutableStateOf(initialPassword) }
    var isEnabled by remember { mutableStateOf(initialEnabled) }
    var isHidden by remember { mutableStateOf(initialHidden) }
    var showPassword by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialSsid.isBlank()) "Create New SSID" else "Edit SSID Configuration",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Selector Band Frekuensi (2.4 GHz vs 5 GHz)
                Text(
                    text = "Select Frequency Band",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !is5G,
                        onClick = { is5G = false },
                        label = { Text("2.4 GHz Band") },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Band24GHz,
                            selectedLabelColor = Color.White
                        )
                    )
                    FilterChip(
                        selected = is5G,
                        onClick = { is5G = true },
                        label = { Text("5 GHz Band") },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Band5GHz,
                            selectedLabelColor = Color.White
                        )
                    )
                }

                // 2. Selector Slot SSID (SSID 1 s/d 4)
                Text(
                    text = "Select SSID Profile Slot",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        1 to "SSID 1 (Main)",
                        2 to "SSID 2 (Guest)",
                        3 to "SSID 3",
                        4 to "SSID 4"
                    ).forEach { (idx, label) ->
                        val selected = slotIndex == idx
                        FilterChip(
                            selected = selected,
                            onClick = { slotIndex = idx },
                            label = { Text(if (selected) label else "Slot $idx") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CrimsonPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                // 3. Switch Enable / Disable SSID
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Broadcast Status",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isEnabled) "SSID is active and broadcasting" else "SSID is turned off (disabled)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { isEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = CrimsonPrimary
                            )
                        )
                    }
                }

                // 4. Input SSID Name
                OutlinedTextField(
                    value = ssidInput,
                    onValueChange = { ssidInput = it },
                    label = { Text("SSID Name (Wi-Fi Name)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CrimsonPrimary,
                        cursorColor = CrimsonPrimary
                    )
                )

                // 5. Input Password
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text("Wi-Fi Password (Min. 8 chars or leave blank for open)") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CrimsonPrimary,
                        cursorColor = CrimsonPrimary
                    )
                )

                // 6. Hide SSID Toggle
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Hide SSID Broadcast",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isHidden) "Hidden network (Manual input required)" else "Visible to all nearby devices",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Switch(
                            checked = isHidden,
                            onCheckedChange = { isHidden = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = CrimsonPrimary
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (ssidInput.isNotBlank()) {
                        // Di FiberHome HG6145F3: wifiIndex 2.4G base = 1..4 (or 2), 5G base = 9..12
                        val computedIndex = if (is5G) {
                            if (slotIndex == 1) 9 else (8 + slotIndex)
                        } else {
                            if (slotIndex == 1) 2 else (1 + slotIndex)
                        }
                        onSave(is5G, computedIndex, ssidInput.trim(), passwordInput.trim(), isEnabled, isHidden)
                    }
                },
                enabled = ssidInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
            ) {
                Text("Apply to Router")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
