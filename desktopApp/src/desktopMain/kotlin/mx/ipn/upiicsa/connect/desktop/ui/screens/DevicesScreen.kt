package mx.ipn.upiicsa.connect.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tablet
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mx.ipn.upiicsa.connect.desktop.domain.DesktopSessionState
import mx.ipn.upiicsa.connect.desktop.ui.DesktopPanelController
import mx.ipn.upiicsa.connect.desktop.ui.DesktopPanelLocalState
import mx.ipn.upiicsa.connect.desktop.ui.ScreenColumn
import mx.ipn.upiicsa.connect.shared.model.ConnectionDirection
import mx.ipn.upiicsa.connect.shared.model.RemoteDeviceDescriptor
import mx.ipn.upiicsa.connect.shared.model.TransportType
import mx.ipn.upiicsa.connect.shared.ui.components.EmptyStatePlaceholder
import mx.ipn.upiicsa.connect.shared.ui.components.SectionCard
import mx.ipn.upiicsa.connect.shared.ui.components.TransportPriorityChip
import mx.ipn.upiicsa.connect.shared.ui.components.TransportTag

/**
 * Pantalla de conexión: dirección del enlace, prioridad de transporte y lista de dispositivos Android descubiertos.
 */
@Composable
fun DevicesScreen(
    controller: DesktopPanelController,
    session: DesktopSessionState,
    local: DesktopPanelLocalState
) {
    ScreenColumn {
        SectionCard(
            title = "Preferencias de enlace",
            subtitle = "El cable USB siempre tiene prioridad automática; Wi-Fi y Bluetooth actúan como respaldo",
            icon = Icons.Rounded.Route
        ) {
            Text("Dirección de la conexión", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ConnectionDirection.entries.forEach { direction ->
                    FilterChip(
                        selected = local.direction == direction,
                        onClick = { controller.setDirection(direction) },
                        leadingIcon = { Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        label = { Text(direction.displayLabel) }
                    )
                }
            }
            Text("Medio preferido", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TransportType.entries.forEach { transport ->
                    TransportPriorityChip(
                        transport = transport,
                        selected = local.preferredTransport == transport,
                        onClick = { controller.setPreferredTransport(transport) }
                    )
                }
            }
        }

        SectionCard(
            title = "Dispositivos Android disponibles",
            subtitle = if (local.isDiscovering) "Buscando por USB, mDNS y Bluetooth…" else "${local.devices.size} encontrado(s)",
            icon = Icons.Rounded.Search,
            trailing = {
                OutlinedButton(onClick = controller::refreshDevices, enabled = !local.isDiscovering) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Buscar de nuevo")
                }
            }
        ) {
            if (local.isDiscovering) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (local.devices.isEmpty() && !local.isDiscovering) {
                EmptyStatePlaceholder(
                    icon = Icons.Rounded.PhoneAndroid,
                    title = "No se encontraron dispositivos",
                    message = "Conecta el teléfono por cable USB con depuración activada o en la misma red Wi-Fi."
                )
            }
            local.devices.forEach { device ->
                FilaDispositivo(
                    device = device,
                    isConnected = session.activeDevice?.deviceId == device.deviceId,
                    activeTransport = session.activeTransport,
                    anotherConnected = session.activeDevice != null && session.activeDevice.deviceId != device.deviceId,
                    onConnect = { controller.connect(device) },
                    onDisconnect = controller::disconnect
                )
            }
        }
    }
}

/**
 * Fila de dispositivo con icono según tipo, medios disponibles y acción de conectar o desconectar.
 */
@Composable
private fun FilaDispositivo(
    device: RemoteDeviceDescriptor,
    isConnected: Boolean,
    activeTransport: TransportType?,
    anotherConnected: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val borderColor = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(if (isConnected) 2.dp else 1.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (device.modelName.contains("Tab", ignoreCase = true)) Icons.Rounded.Tablet else Icons.Rounded.PhoneAndroid,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(device.deviceName, style = MaterialTheme.typography.titleSmall)
            Text(
                text = "${device.modelName} · Android SDK ${device.androidSdkVersion ?: "N/D"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                device.availableTransports.sortedBy { it.wireId }.forEach { transport ->
                    TransportTag(
                        transport = transport,
                        highlighted = if (isConnected) transport == activeTransport else transport == device.preferredTransport
                    )
                }
            }
        }
        if (isConnected) {
            OutlinedButton(onClick = onDisconnect) {
                Icon(Icons.Rounded.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Desconectar")
            }
        } else {
            Button(onClick = onConnect, enabled = !anotherConnected) {
                Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Conectar")
            }
        }
    }
}
