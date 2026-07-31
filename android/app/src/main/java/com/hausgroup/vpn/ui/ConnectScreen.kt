package com.hausgroup.vpn.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hausgroup.vpn.ConnectionStatus
import com.hausgroup.vpn.VpnViewModel
import com.hausgroup.vpn.ui.theme.HausDanger
import com.hausgroup.vpn.ui.theme.HausGold
import com.hausgroup.vpn.ui.theme.HausSurface
import com.hausgroup.vpn.ui.theme.HausSurfaceAlt
import com.hausgroup.vpn.ui.theme.HausTeal
import com.hausgroup.vpn.ui.theme.HausTextMuted
import com.hausgroup.vpn.ui.theme.HausTextPrimary

@Composable
fun ConnectScreen(
    viewModel: VpnViewModel,
    onConnectRequested: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    var showConfigSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.Shield, contentDescription = null, tint = HausTeal, modifier = Modifier.size(22.dp))
            Spacer(Modifier.size(8.dp))
            Text("HAUS", color = HausTextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 3.sp)
            Text("VPN", color = HausGold, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 3.sp)
        }

        Spacer(Modifier.height(56.dp))

        ConnectDial(status = state.status, onClick = onConnectRequested)

        Spacer(Modifier.height(28.dp))

        Text(
            text = statusLabel(state.status),
            color = statusColor(state.status),
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        state.message?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = HausTextMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(40.dp))

        ServerCard(serverName = state.serverName)

        Spacer(Modifier.height(16.dp))

        StatsRow(
            connected = state.status == ConnectionStatus.CONNECTED,
            rx = state.rxBytes,
            tx = state.txBytes,
        )

        Spacer(Modifier.weight(1f))

        TextButton(onClick = { showConfigSheet = true }) {
            Text(
                if (state.hasConfig) "Update server configuration" else "Add server configuration",
                color = HausTeal,
            )
        }
        Spacer(Modifier.height(16.dp))
    }

    if (showConfigSheet) {
        ConfigDialog(
            onDismiss = { showConfigSheet = false },
            onSave = { raw ->
                viewModel.saveConfig(raw)
                showConfigSheet = false
            },
        )
    }
}

@Composable
private fun ConnectDial(status: ConnectionStatus, onClick: () -> Unit) {
    val active = status == ConnectionStatus.CONNECTED
    val glow by animateFloatAsState(
        targetValue = if (active) 1f else 0.35f,
        animationSpec = tween(600),
        label = "glow",
    )
    val ring = when (status) {
        ConnectionStatus.CONNECTED -> HausTeal
        ConnectionStatus.CONNECTING -> HausGold
        ConnectionStatus.ERROR -> HausDanger
        else -> HausTextMuted
    }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
        Box(
            modifier = Modifier
                .size(220.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(ring.copy(alpha = 0.18f * glow), HausSurface.copy(alpha = 0f)),
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(168.dp)
                .clip(CircleShape)
                .background(HausSurface)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (active) Icons.Filled.Bolt else Icons.Filled.Public,
                    contentDescription = null,
                    tint = ring,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (active) "TAP TO STOP" else "TAP TO CONNECT",
                    color = HausTextMuted,
                    fontSize = 11.sp,
                    letterSpacing = 1.5.sp,
                )
            }
        }
    }
}

@Composable
private fun ServerCard(serverName: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HausSurface)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Public, contentDescription = null, tint = HausGold, modifier = Modifier.size(24.dp))
        Spacer(Modifier.size(14.dp))
        Column {
            Text("Selected server", color = HausTextMuted, fontSize = 12.sp)
            Text(serverName, color = HausTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun StatsRow(connected: Boolean, rx: Long, tx: Long) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StatTile("Download", if (connected) formatBytes(rx) else "—", Modifier.weight(1f))
        StatTile("Upload", if (connected) formatBytes(tx) else "—", Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(HausSurfaceAlt)
            .padding(vertical = 16.dp, horizontal = 18.dp),
    ) {
        Text(label, color = HausTextMuted, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = HausTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ConfigDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onSave(text) }) {
                Text("Save", color = HausTeal)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = HausTextMuted) } },
        title = { Text("Server configuration", color = HausTextPrimary) },
        text = {
            Column {
                Text(
                    "Paste a WireGuard configuration (wg-quick format). " +
                        "This connects the app to any WireGuard server today; the HausVPN " +
                        "backend will hand these out automatically once live.",
                    color = HausTextMuted,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    placeholder = { Text("[Interface]\nPrivateKey = ...\nAddress = ...\n\n[Peer]\nPublicKey = ...\nEndpoint = ...:51820\nAllowedIPs = 0.0.0.0/0, ::/0") },
                )
            }
        },
        containerColor = HausSurface,
    )
}

private fun statusLabel(status: ConnectionStatus) = when (status) {
    ConnectionStatus.DISCONNECTED -> "Not connected"
    ConnectionStatus.CONNECTING -> "Connecting…"
    ConnectionStatus.CONNECTED -> "Protected"
    ConnectionStatus.ERROR -> "Connection failed"
}

private fun statusColor(status: ConnectionStatus) = when (status) {
    ConnectionStatus.CONNECTED -> HausTeal
    ConnectionStatus.CONNECTING -> HausGold
    ConnectionStatus.ERROR -> HausDanger
    else -> HausTextMuted
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    return String.format("%.2f GB", mb / 1024.0)
}
