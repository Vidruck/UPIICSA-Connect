package mx.ipn.upiicsa.connect.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.launch
import mx.ipn.upiicsa.connect.desktop.adapters.os.OsAdapterFactory
import mx.ipn.upiicsa.connect.desktop.application.DesktopConnectionService
import mx.ipn.upiicsa.connect.desktop.domain.DesktopSessionState
import mx.ipn.upiicsa.connect.shared.model.ConnectionDirection
import mx.ipn.upiicsa.connect.shared.model.ConnectionStatus
import mx.ipn.upiicsa.connect.shared.model.NotificationResolutionAction
import mx.ipn.upiicsa.connect.shared.model.PhoneNotificationItem
import mx.ipn.upiicsa.connect.shared.model.RemoteDeviceDescriptor
import mx.ipn.upiicsa.connect.shared.model.SecondaryDisplayConfig
import mx.ipn.upiicsa.connect.shared.model.ThemePreference
import mx.ipn.upiicsa.connect.shared.model.TransportType
import mx.ipn.upiicsa.connect.shared.ui.theme.UpiicsaConnectTheme

/**
 * Punto de entrada de la aplicación de escritorio que ensambla la fábrica del sistema operativo e inicia el panel gráfico.
 */
fun main() = application {
    val hostPlatform = remember { OsAdapterFactory.detectHostPlatform() }
    val connectionService = remember { OsAdapterFactory.createDesktopService(hostPlatform) }
    var themePreference by remember { mutableStateOf(ThemePreference.SYSTEM_AUTO) }

    Window(
        onCloseRequest = ::exitApplication,
        title = "UPIICSA-Connect (${hostPlatform.name})"
    ) {
        UpiicsaConnectTheme(themePreference = themePreference) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                DesktopControlPanelScreen(
                    service = connectionService,
                    platformLabel = hostPlatform.name,
                    themePreference = themePreference,
                    onThemeChange = { themePreference = it }
                )
            }
        }
    }
}

/**
 * Panel principal de configuración, enlace y resolución de notificaciones en tiempo real para escritorio Windows/GNU-Linux.
 */
@Composable
fun DesktopControlPanelScreen(
    service: DesktopConnectionService,
    platformLabel: String,
    themePreference: ThemePreference,
    onThemeChange: (ThemePreference) -> Unit
) {
    val scope = rememberCoroutineScope()
    var sessionState by remember { mutableStateOf(DesktopSessionState()) }
    var discoveredDevices by remember { mutableStateOf<List<RemoteDeviceDescriptor>>(emptyList()) }
    var selectedDirection by remember { mutableStateOf(ConnectionDirection.DESKTOP_TO_MOBILE) }
    var preferredTransport by remember { mutableStateOf(TransportType.USB_CABLE) }
    var clipboardDraft by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        discoveredDevices = service.discoverAvailableDevices()
        sessionState = service.syncIncomingNotifications(crearNotificacionesDummy())
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            PanelEncabezadoInstitucional(
                platformLabel = platformLabel,
                statusMessage = sessionState.statusMessage,
                connectionStatus = sessionState.status,
                themePreference = themePreference,
                onThemeChange = onThemeChange
            )
        }

        item {
            PanelConexionYTransporte(
                devices = discoveredDevices,
                selectedDirection = selectedDirection,
                preferredTransport = preferredTransport,
                connectedDeviceId = sessionState.activeDevice?.deviceId,
                onDirectionChange = { selectedDirection = it },
                onTransportChange = { preferredTransport = it },
                onConnectDevice = { device ->
                    scope.launch {
                        sessionState = service.connectToDevice(device, preferredTransport, selectedDirection)
                    }
                },
                onDisconnect = {
                    scope.launch {
                        sessionState = service.disconnect()
                    }
                }
            )
        }

        item {
            PanelFuncionesConvergencia(
                state = sessionState,
                clipboardDraft = clipboardDraft,
                onClipboardDraftChange = { clipboardDraft = it },
                onPushClipboard = {
                    scope.launch {
                        sessionState = service.pushClipboardText(clipboardDraft)
                    }
                },
                onToggleSecondaryDisplay = { enabled ->
                    scope.launch {
                        sessionState = service.toggleSecondaryDisplay(
                            SecondaryDisplayConfig(enabled = enabled)
                        )
                    }
                },
                onToggleCameraMic = { camEnabled, micEnabled ->
                    scope.launch {
                        sessionState = service.toggleVirtualCameraAndMic(
                            enableCamera = camEnabled,
                            enableMicrophone = micEnabled,
                            useBackCamera = false
                        )
                    }
                },
                onToggleMusicMode = { enabled, gain ->
                    scope.launch {
                        sessionState = service.toggleMusicMode(
                            enabled = enabled,
                            gainBoostFactor = gain,
                            trackTitle = if (enabled) "Audio Móvil -> Tarjeta PC" else "Sin reproducción activa"
                        )
                    }
                }
            )
        }

        item {
            Text(
                text = "Resolución de Notificaciones del Teléfono (Sin distracciones)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        items(sessionState.notifications, key = { it.notificationId }) { item ->
            TarjetaResolucionNotificacion(
                notification = item,
                onResolve = { action ->
                    scope.launch {
                        sessionState = service.resolveNotification(action)
                    }
                }
            )
        }
    }
}

/**
 * Renderiza la cabecera institucional UPIICSA-IPN y el selector de modo claro u oscuro adaptable.
 */
@Composable
private fun PanelEncabezadoInstitucional(
    platformLabel: String,
    statusMessage: String,
    connectionStatus: ConnectionStatus,
    themePreference: ThemePreference,
    onThemeChange: (ThemePreference) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "UPIICSA-Connect | Escritorio ($platformLabel)",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Estado: ${connectionStatus.name} — $statusMessage",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemePreference.entries.forEach { mode ->
                    FilterChip(
                        selected = themePreference == mode,
                        onClick = { onThemeChange(mode) },
                        label = { Text(mode.displayLabel) }
                    )
                }
            }
        }
    }
}

/**
 * Renderiza el selector de dirección de enlace, prioridad de transporte (USB 1ª / Wi-Fi-BT 2ª) y lista de dispositivos.
 */
@Composable
private fun PanelConexionYTransporte(
    devices: List<RemoteDeviceDescriptor>,
    selectedDirection: ConnectionDirection,
    preferredTransport: TransportType,
    connectedDeviceId: String?,
    onDirectionChange: (ConnectionDirection) -> Unit,
    onTransportChange: (TransportType) -> Unit,
    onConnectDevice: (RemoteDeviceDescriptor) -> Unit,
    onDisconnect: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Opciones de Conexión y Transporte",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ConnectionDirection.entries.forEach { dir ->
                    FilterChip(
                        selected = selectedDirection == dir,
                        onClick = { onDirectionChange(dir) },
                        label = { Text(dir.displayLabel) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TransportType.entries.forEach { transport ->
                    FilterChip(
                        selected = preferredTransport == transport,
                        onClick = { onTransportChange(transport) },
                        label = { Text(transport.displayLabel) }
                    )
                }
            }

            devices.forEach { device ->
                val isConnected = connectedDeviceId == device.deviceId
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(device.deviceName, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "${device.modelName} | SDK ${device.androidSdkVersion ?: "N/D"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (isConnected) {
                        OutlinedButton(onClick = onDisconnect) {
                            Text("Desconectar")
                        }
                    } else {
                        Button(onClick = { onConnectDevice(device) }) {
                            Text("Conectar")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Renderiza los controles de Portapapeles, Monitor Secundario, Cámara/Micrófono de videollamadas y Music Mode.
 */
@Composable
private fun PanelFuncionesConvergencia(
    state: DesktopSessionState,
    clipboardDraft: String,
    onClipboardDraftChange: (String) -> Unit,
    onPushClipboard: () -> Unit,
    onToggleSecondaryDisplay: (Boolean) -> Unit,
    onToggleCameraMic: (Boolean, Boolean) -> Unit,
    onToggleMusicMode: (Boolean, Float) -> Unit
) {
    var gainSlider by remember { mutableStateOf(state.featureToggles.musicMode.gainBoostFactor) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Servicios de Continuidad y Periféricos Remotos",
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
                    label = { Text("Portapapeles Compartido") },
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = onPushClipboard) {
                    Text("Enviar")
                }
            }

            FilaInterruptorServicio(
                titulo = "Monitor Secundario (Tablet/Android como pantalla PC)",
                subtitulo = "Resolución objetivo: ${state.featureToggles.secondaryDisplay.widthPx}x${state.featureToggles.secondaryDisplay.heightPx}",
                checked = state.featureToggles.secondaryDisplay.enabled,
                onCheckedChange = onToggleSecondaryDisplay
            )

            FilaInterruptorServicio(
                titulo = "Cámara y Micrófono del Teléfono (Videollamadas)",
                subtitulo = "Expone webcam y micrófono virtual en el portátil",
                checked = state.featureToggles.remoteCameraMic.cameraEnabled,
                onCheckedChange = { enabled -> onToggleCameraMic(enabled, enabled) }
            )

            FilaInterruptorServicio(
                titulo = "Music Mode (Audio del teléfono -> Tarjeta de sonido PC)",
                subtitulo = "Ahorra recursos del PC delegando reproducción al celular",
                checked = state.featureToggles.musicMode.enabled,
                onCheckedChange = { enabled -> onToggleMusicMode(enabled, gainSlider) }
            )

            if (state.featureToggles.musicMode.enabled) {
                Text("Refuerzo de ganancia DSP (Music Mode): ${"%.2f".format(gainSlider)}x")
                Slider(
                    value = gainSlider,
                    onValueChange = { gainSlider = it },
                    valueRange = 0.8f..2.0f,
                    onValueChangeFinished = { onToggleMusicMode(true, gainSlider) }
                )
            }
        }
    }
}

/**
 * Componente reutilizable para mostrar una fila de activación de servicio con título y subtítulo descriptivo.
 */
@Composable
private fun FilaInterruptorServicio(
    titulo: String,
    subtitulo: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(titulo, fontWeight = FontWeight.SemiBold)
            Text(subtitulo, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * Muestra una notificación móvil espejada permitiendo responderla, marcarla como leída o descartarla desde la PC.
 */
@Composable
private fun TarjetaResolucionNotificacion(
    notification: PhoneNotificationItem,
    onResolve: (NotificationResolutionAction) -> Unit
) {
    var quickReply by remember { mutableStateOf("") }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (notification.isResolved) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "${notification.appName} • ${notification.senderTitle}",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Text(notification.timestampLabel, style = MaterialTheme.typography.bodySmall)
            }
            Text(notification.messageBody)

            if (notification.isResolved) {
                Text(
                    text = "Resuelta: ${notification.resolutionSummary.orEmpty()}",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                if (notification.supportsInlineReply) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = quickReply,
                            onValueChange = { quickReply = it },
                            label = { Text("Responder desde ordenador") },
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            enabled = quickReply.isNotBlank(),
                            onClick = {
                                onResolve(
                                    NotificationResolutionAction.QuickReply(
                                        notificationId = notification.notificationId,
                                        replyText = quickReply
                                    )
                                )
                                quickReply = ""
                            }
                        ) {
                            Text("Enviar")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onResolve(NotificationResolutionAction.MarkAsRead(notification.notificationId)) }
                    ) {
                        Text("Marcar leída")
                    }
                    OutlinedButton(
                        onClick = { onResolve(NotificationResolutionAction.Dismiss(notification.notificationId)) }
                    ) {
                        Text("Descartar")
                    }
                }
            }
        }
    }
}

/**
 * Construye notificaciones de ejemplo para demostrar la resolución remota desde la interfaz de escritorio.
 */
private fun crearNotificacionesDummy(): List<PhoneNotificationItem> {
    // [DUMMY-PRUEBAS]: Datos iniciales hardcodeados para probar respuesta remota y resolución en escritorio sin teléfono en vivo.
    return listOf(
        PhoneNotificationItem(
            notificationId = "notif-1",
            appName = "WhatsApp",
            packageName = "com.whatsapp",
            senderTitle = "Equipo Proyecto Móvil",
            messageBody = "¿Ya quedó lista la arquitectura Ports & Adapters para Linux y Windows?",
            timestampLabel = "14:28",
            supportsInlineReply = true
        ),
        PhoneNotificationItem(
            notificationId = "notif-2",
            appName = "Classroom IPN",
            packageName = "com.google.android.apps.classroom",
            senderTitle = "Programación Móvil - 7mo",
            messageBody = "Recordatorio: entrega de avance de tercer parcial.",
            timestampLabel = "14:15",
            supportsInlineReply = false
        )
    )
}
