package com.example.threeblindcubers.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.threeblindcubers.domain.models.ConnectionState
import com.example.threeblindcubers.ui.timer.DiscoveredDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    connectionState: ConnectionState,
    connectedDeviceName: String?,
    discoveredDevices: List<DiscoveredDevice>,
    isScanning: Boolean,
    hasAxisCalibration: Boolean,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnectDevice: (DiscoveredDevice) -> Unit,
    onDisconnect: () -> Unit,
    onResync: () -> Unit,
    onCalibrateAxes: () -> Unit,
    onOpenDebugTools: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Connection status
            ConnectionStatusCard(
                connectionState = connectionState,
                connectedDeviceName = connectedDeviceName
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Connection controls
            when (connectionState) {
                ConnectionState.DISCONNECTED, ConnectionState.ERROR -> {
                    Button(
                        onClick = onStartScan,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Scan for Cube")
                    }

                    if (connectionState == ConnectionState.ERROR) {
                        Text(
                            text = "Connection error. Try scanning again.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                ConnectionState.SCANNING -> {
                    // Discovered devices list
                    if (discoveredDevices.isEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Scanning for cubes...")
                        }
                    } else {
                        Text(
                            text = "Found ${discoveredDevices.size} device(s):",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        LazyColumn(
                            modifier = Modifier.height((discoveredDevices.size * 56).coerceAtMost(224).dp)
                        ) {
                            items(discoveredDevices) { device ->
                                DeviceItem(
                                    device = device,
                                    onClick = { onConnectDevice(device) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = onStopScan,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Stop Scanning")
                    }
                }

                ConnectionState.CONNECTING -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Connecting...")
                    }
                }

                ConnectionState.CONNECTED -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDisconnect,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Disconnect")
                        }
                        Button(
                            onClick = onResync,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Resync")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = onCalibrateAxes,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (hasAxisCalibration) "Recalibrate IMU Axes"
                            else "Calibrate IMU Axes"
                        )
                    }
                }
            }

            // Debug tools hidden from UI (code retained)
            // To re-enable, uncomment the block below:
            // Spacer(modifier = Modifier.height(16.dp))
            // HorizontalDivider()
            // Spacer(modifier = Modifier.height(16.dp))
            // Row(
            //     modifier = Modifier
            //         .fillMaxWidth()
            //         .clickable(onClick = onOpenDebugTools)
            //         .padding(vertical = 12.dp),
            //     verticalAlignment = Alignment.CenterVertically
            // ) {
            //     Icon(
            //         Icons.Default.Build,
            //         contentDescription = null,
            //         tint = MaterialTheme.colorScheme.onSurfaceVariant
            //     )
            //     Spacer(modifier = Modifier.width(12.dp))
            //     Text(
            //         text = "Debug Tools",
            //         style = MaterialTheme.typography.bodyLarge,
            //         color = MaterialTheme.colorScheme.onSurfaceVariant
            //     )
            // }
        }
    }
}

@Composable
private fun ConnectionStatusCard(
    connectionState: ConnectionState,
    connectedDeviceName: String?
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when (connectionState) {
                ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primaryContainer
                ConnectionState.ERROR -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when (connectionState) {
                    ConnectionState.DISCONNECTED -> "Disconnected"
                    ConnectionState.SCANNING -> "Scanning..."
                    ConnectionState.CONNECTING -> "Connecting..."
                    ConnectionState.CONNECTED -> "Connected"
                    ConnectionState.ERROR -> "Error"
                },
                style = MaterialTheme.typography.titleMedium
            )
            if (connectedDeviceName != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "($connectedDeviceName)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DeviceItem(
    device: DiscoveredDevice,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = device.name,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = device.address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
