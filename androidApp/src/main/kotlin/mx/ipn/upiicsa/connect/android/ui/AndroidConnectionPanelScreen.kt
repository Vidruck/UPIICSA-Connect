package mx.ipn.upiicsa.connect.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mx.ipn.upiicsa.connect.android.viewmodel.AndroidConnectionUiState
import mx.ipn.upiicsa.connect.android.viewmodel.AndroidConnectionViewModel
import mx.ipn.upiicsa.connect.shared.model.ConnectionDirection
import mx.ipn.upiicsa.connect.shared.model.NotificationResolutionAction
import mx.ipn.upiicsa.connect.shared.model.PhoneNotificationItem
import mx.ipn.upiicsa.connect.shared.model.ThemePreference
import mx.ipn.upiicsa.connect.shared.model.TransportType
import mx.ipn.upiicsa.connect.shared.ui.theme.UpiicsaConnectTheme

/**
 * Vista principal Compose en Android (capa View MVVM) que observa el StateFlow sin contener lógica de negocio.
 */
@Composable
fun AndroidConnectionPanelScreen(
    viewModel: AndroidConnectionViewModel = remember { AndroidConnectionViewModel.createDefault() }
) {
    val uiState by viewModel.uiState.collectAsState()
    var clipboardDraft by remember { mutableStateOf("") }

    UpiicsaConnectTheme(themePreference = uiState.themePreference) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    EncabezadoMovilUpiicsa(
                        uiState = uiState,
                        onThemeSelect = viewModel::setThemePreference
                    )
                }

                item {
                    PanelEnlaceOrdenador(
                        uiState = uiState,
                        onDirectionSelect = viewModel::setConnectionDirection,
                        onTransportSelect = viewModel::setPreferredTransport,
                        onConnect = viewModel::connectToDesktop,
                        onDisconnect = viewModel::disconnectDesktop
                    )
                }

                item {
                    PanelOpcionesServiciosMovil(
                        uiState = uiState,
                        clipboardDraft = clipboardDraft,
                        onClipboardDraftChange = { clipboardDraft = it },
                        onSendClipboard = { viewModel.syncClipboard(clipboardDraft) },
                        onToggleNotifications = viewModel::toggleNotificationBridge,
                        onToggleMusicMode = viewModel::toggleMusicMode,
                        onToggleCameraMic = { enabled -> viewModel.toggleCameraAndMic(enabled, useBackCamera = false) },
                        onToggleSecondaryDisplay = viewModel::toggleSecondaryDisplay
                    )
                }

                item {
                    Text(
                        text = "Notificaciones Compartidas (Resolvibles en PC)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                items(uiState.notifications, key = { it.notificationId }) { notif ->
                    TarjetaNotificacionMovil(
                        notification = notif,
                        onSimulateDesktopReply = { id ->
                            viewModel.simulateResolveFromDesktop(
                                NotificationResolutionAction.QuickReply(id, "Recibido desde mi PC")
                            )
                        }
                    )
                }
            }
        }
    }
}

/**
 * Muestra la cabecera institucional UPIICSA-IPN y las opciones de modo claro/oscuro en el teléfono.
 */
@Composable
private fun EncabezadoMovilUpiicsa(
    uiState: AndroidConnectionUiState,
    onThemeSelect: (ThemePreference) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "UPIICSA-Connect | Móvil Android",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = "${uiState.status.name}: ${uiState.statusMessage}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ThemePreference.entries.forEach { pref ->
                    FilterChip(
                        selected = uiState.themePreference == pref,
                        onClick = { onThemeSelect(pref) },
                        label = { Text(pref.displayLabel) }
                    )
                }
            }
        }
    }
}

/**
 * Muestra las opciones para conectar del móvil al ordenador o aceptar desde el ordenador, con prioridad USB -> Wi-Fi/BT.
 */
@Composable
private fun PanelEnlaceOrdenador(
    uiState: AndroidConnectionUiState,
    onDirectionSelect: (ConnectionDirection) -> Unit,
    onTransportSelect: (TransportType) -> Unit,
    onConnect: (mx.ipn.upiicsa.connect.shared.model.RemoteDeviceDescriptor) -> Unit,
    onDisconnect: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Conexión Móvil <-> Ordenador",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ConnectionDirection.entries.forEach { dir ->
                    FilterChip(
                        selected = uiState.direction == dir,
                        onClick = { onDirectionSelect(dir) },
                        label = { Text(dir.displayLabel) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TransportType.entries.forEach { transport ->
                    FilterChip(
                        selected = uiState.preferredTransport == transport,
                        onClick = { onTransportSelect(transport) },
                        label = { Text(transport.displayLabel) }
                    )
                }
            }

            uiState.availableDesktops.forEach { desktop ->
                val isLinked = uiState.connectedDesktop?.deviceId == desktop.deviceId
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(desktop.deviceName, fontWeight = FontWeight.SemiBold)
                        Text(desktop.modelName, style = MaterialTheme.typography.bodySmall)
                    }
                    if (isLinked) {
                        OutlinedButton(onClick = onDisconnect) {
                            Text("Desconectar")
                        }
                    } else {
                        Button(onClick = { onConnect(desktop) }) {
                            Text("Enlazar")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Renderiza los interruptores para habilitar notificaciones, portapapeles, Music Mode, webcam/mic y monitor secundario.
 */
@Composable
private fun PanelOpcionesServiciosMovil(
    uiState: AndroidConnectionUiState,
    clipboardDraft: String,
    onClipboardDraftChange: (String) -> Unit,
    onSendClipboard: () -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onToggleMusicMode: (Boolean) -> Unit,
    onToggleCameraMic: (Boolean) -> Unit,
    onToggleSecondaryDisplay: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Panel de Opciones y Servicios",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = clipboardDraft,
                    onValueChange = onClipboardDraftChange,
                    label = { Text("Enviar portapapeles a PC") },
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = onSendClipboard) {
                    Text("Enviar")
                }
            }

            InterruptorOpcionMovil(
                titulo = "Compartir y resolver notificaciones",
                checked = uiState.featureToggles.syncNotifications,
                onCheckedChange = onToggleNotifications
            )

            InterruptorOpcionMovil(
                titulo = "Music Mode (Audio móvil -> Tarjeta de PC)",
                checked = uiState.featureToggles.musicMode.enabled,
                onCheckedChange = onToggleMusicMode
            )

            InterruptorOpcionMovil(
                titulo = "Cámara y micrófono para videollamadas PC",
                checked = uiState.featureToggles.remoteCameraMic.cameraEnabled,
                onCheckedChange = onToggleCameraMic
            )

            InterruptorOpcionMovil(
                titulo = "Usar dispositivo como Monitor Secundario",
                checked = uiState.featureToggles.secondaryDisplay.enabled,
                onCheckedChange = onToggleSecondaryDisplay
            )
        }
    }
}

/**
 * Componente visual compacto para activar o desactivar una capacidad del panel móvil.
 */
@Composable
private fun InterruptorOpcionMovil(
    titulo: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(titulo, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * Muestra el estado de una notificación compartida y permite simular su resolución remota desde el ordenador.
 */
@Composable
private fun TarjetaNotificacionMovil(
    notification: PhoneNotificationItem,
    onSimulateDesktopReply: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "${notification.appName} • ${notification.senderTitle}",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary
            )
            Text(notification.messageBody)
            if (notification.isResolved) {
                Text(
                    text = notification.resolutionSummary.orEmpty(),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                OutlinedButton(onClick = { onSimulateDesktopReply(notification.notificationId) }) {
                    Text("Simular resolución desde PC")
                }
            }
        }
    }
}
