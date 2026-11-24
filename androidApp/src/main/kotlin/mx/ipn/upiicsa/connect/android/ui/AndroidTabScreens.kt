package mx.ipn.upiicsa.connect.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Videocam
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import mx.ipn.upiicsa.connect.android.viewmodel.AndroidConnectionUiState
import mx.ipn.upiicsa.connect.shared.model.ConnectionDirection
import mx.ipn.upiicsa.connect.shared.model.ConnectionStatus
import mx.ipn.upiicsa.connect.shared.model.NotificationResolutionAction
import mx.ipn.upiicsa.connect.shared.model.RemoteDeviceDescriptor
import mx.ipn.upiicsa.connect.shared.model.TransportType
import mx.ipn.upiicsa.connect.shared.ui.components.ActivityLogList
import mx.ipn.upiicsa.connect.shared.ui.components.AudioLevelMeter
import mx.ipn.upiicsa.connect.shared.ui.components.CameraPreviewPlaceholder
import mx.ipn.upiicsa.connect.shared.ui.components.EmptyStatePlaceholder
import mx.ipn.upiicsa.connect.shared.ui.components.MetricTile
import mx.ipn.upiicsa.connect.shared.ui.components.NotificationResolutionCard
import mx.ipn.upiicsa.connect.shared.ui.components.PairingPinDisplay
import mx.ipn.upiicsa.connect.shared.ui.components.SecondaryDisplayPreview
import mx.ipn.upiicsa.connect.shared.ui.components.SectionCard
import mx.ipn.upiicsa.connect.shared.ui.components.ServiceToggleRow
import mx.ipn.upiicsa.connect.shared.ui.components.TransportPriorityChip
import mx.ipn.upiicsa.connect.shared.ui.components.TransportTag
import mx.ipn.upiicsa.connect.shared.ui.theme.UpiicsaPalette
import mx.ipn.upiicsa.connect.shared.ui.theme.UpiicsaThemeTokens
import mx.ipn.upiicsa.connect.shared.util.DisplayFormatters
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Formateador de hora local para la bitácora de actividad (java.time disponible desde API 26).
 */
private val relojLocal: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

/**
 * Convierte milisegundos de época en hora local "HH:mm:ss".
 */
private fun horaLocal(epochMillis: Long): String = relojLocal.format(Instant.ofEpochMilli(epochMillis))

/**
 * Contenedor desplazable común de cada pestaña.
 */
@Composable
private fun TabColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content
    )
}

/**
 * Pestaña de enlace: tarjeta de sesión con PIN, preferencias de transporte, ordenadores disponibles y bitácora.
 */
@Composable
fun EnlaceTab(
    uiState: AndroidConnectionUiState,
    onDirectionSelect: (ConnectionDirection) -> Unit,
    onTransportSelect: (TransportType) -> Unit,
    onConnect: (RemoteDeviceDescriptor) -> Unit,
    onDisconnect: () -> Unit,
    onRescan: () -> Unit,
    onRealLinkToggle: (Boolean) -> Unit
) {
    val tokens = UpiicsaThemeTokens.extendedColors
    TabColumn {
        Surface(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .background(Brush.linearGradient(listOf(UpiicsaPalette.VerdeUpiicsaOscuro, UpiicsaPalette.VerdeUpiicsa)))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = uiState.connectedDesktop?.deviceName ?: "Sin ordenador enlazado",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Text(
                    text = uiState.activeTransport?.let { "${uiState.direction.displayLabel} · ${it.displayLabel}" }
                        ?: "Conecta por cable USB (prioridad 1) o Wi-Fi / Bluetooth (prioridad 2)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )
                Text(
                    text = uiState.coreEngineLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
                if (uiState.pairingPin != null) {
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Confirma este PIN en el ordenador", style = MaterialTheme.typography.labelLarge)
                            PairingPinDisplay(pin = uiState.pairingPin)
                        }
                    }
                    OutlinedButton(onClick = onDisconnect) {
                        Icon(Icons.Rounded.LinkOff, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Text("  Desconectar", color = Color.White)
                    }
                }
            }
        }

        if (uiState.status == ConnectionStatus.CONNECTED) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricTile(
                    icon = Icons.Rounded.Speed,
                    label = "Latencia",
                    value = "${uiState.linkTelemetry.latencyMs} ms",
                    accent = tokens.info,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    icon = Icons.Rounded.SignalCellularAlt,
                    label = "Señal",
                    value = "${uiState.linkTelemetry.signalQualityPercent} %",
                    accent = tokens.success,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        SectionCard(title = "Preferencias de enlace", subtitle = "USB siempre tiene prioridad automática", icon = Icons.Rounded.Route) {
            ServiceToggleRow(
                icon = Icons.Rounded.Usb,
                title = "Enlace real por cable USB",
                subtitle = "Requiere depuración USB y el escritorio iniciado con -Dupiicsa.enlace=real (adb reverse)",
                checked = uiState.useRealLink,
                onCheckedChange = onRealLinkToggle
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ConnectionDirection.entries.forEach { direction ->
                    FilterChip(
                        selected = uiState.direction == direction,
                        onClick = { onDirectionSelect(direction) },
                        label = { Text(direction.displayLabel) }
                    )
                }
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TransportType.entries.forEach { transport ->
                    TransportPriorityChip(
                        transport = transport,
                        selected = uiState.preferredTransport == transport,
                        onClick = { onTransportSelect(transport) }
                    )
                }
            }
        }

        SectionCard(
            title = "Ordenadores disponibles",
            subtitle = if (uiState.isScanning) "Buscando…" else "${uiState.availableDesktops.size} encontrado(s)",
            icon = Icons.Rounded.Computer,
            trailing = {
                OutlinedButton(onClick = onRescan, enabled = !uiState.isScanning) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Buscar de nuevo", modifier = Modifier.size(18.dp))
                }
            }
        ) {
            if (uiState.isScanning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            uiState.availableDesktops.forEach { desktop ->
                val isConnected = uiState.connectedDesktop?.deviceId == desktop.deviceId
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            if (isConnected) 2.dp else 1.dp,
                            if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(16.dp)
                        )
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Laptop, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(desktop.deviceName, style = MaterialTheme.typography.titleSmall)
                            Text(desktop.modelName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        desktop.availableTransports.forEach { transport ->
                            TransportTag(transport, highlighted = if (isConnected) transport == uiState.activeTransport else transport == desktop.preferredTransport)
                        }
                        Box(modifier = Modifier.weight(1f))
                        if (!isConnected) {
                            Button(onClick = { onConnect(desktop) }, enabled = uiState.connectedDesktop == null) {
                                Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("  Conectar")
                            }
                        }
                    }
                }
            }
        }

        SectionCard(title = "Actividad reciente", icon = Icons.Rounded.History) {
            if (uiState.activityLog.isEmpty()) {
                EmptyStatePlaceholder(Icons.Rounded.History, "Sin actividad", "Aquí verás conexiones y cambios de servicios.")
            } else {
                ActivityLogList(entries = uiState.activityLog, timeFormatter = ::horaLocal, maxEntries = 6)
            }
        }
    }
}

/**
 * Pestaña de avisos: puente de notificaciones y simulación de las resoluciones que llegan desde el ordenador.
 */
@Composable
fun AvisosTab(
    uiState: AndroidConnectionUiState,
    onToggleBridge: (Boolean) -> Unit,
    onSimulateResolution: (NotificationResolutionAction) -> Unit
) {
    TabColumn {
        SectionCard(title = "Puente de notificaciones", icon = Icons.Rounded.NotificationsActive, accent = MaterialTheme.colorScheme.tertiary) {
            ServiceToggleRow(
                icon = Icons.Rounded.NotificationsActive,
                title = "Espejar en el ordenador",
                subtitle = "Responde, marca como leída o descarta desde la PC",
                checked = uiState.featureToggles.syncNotifications,
                onCheckedChange = onToggleBridge,
                accent = MaterialTheme.colorScheme.tertiary
            )
            Text(
                text = if (uiState.useRealLink) {
                    "Las acciones de cada tarjeta se aplican sobre la notificación real del teléfono, igual que las órdenes de la PC."
                } else {
                    "Las acciones de cada tarjeta simulan la orden que enviaría el ordenador por el canal de notificaciones."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (uiState.useRealLink) {
            NotificationAccessCard(granted = uiState.notificationAccessGranted)
        }
        if (uiState.notifications.isEmpty()) {
            EmptyStatePlaceholder(Icons.Rounded.NotificationsActive, "Sin notificaciones", "Las notificaciones capturadas aparecerán aquí.")
        }
        uiState.notifications.forEach { item ->
            NotificationResolutionCard(
                notification = item,
                onResolve = onSimulateResolution,
                replyPlaceholder = "Simular respuesta desde la PC"
            )
        }
    }
}

/**
 * Tarjeta que indica si Android concedió el acceso a notificaciones y abre sus ajustes cuando falta.
 */
@Composable
private fun NotificationAccessCard(granted: Boolean) {
    val context = LocalContext.current
    SectionCard(title = "Acceso a notificaciones", icon = Icons.Rounded.NotificationsActive, accent = MaterialTheme.colorScheme.tertiary) {
        Text(
            text = if (granted) {
                "Concedido: las notificaciones reales del teléfono se espejan en el ordenador."
            } else {
                "Sin acceso: Android no permite leer las notificaciones. Concédelo para espejarlas y responderlas desde la PC."
            },
            style = MaterialTheme.typography.bodyMedium
        )
        if (!granted) {
            Button(onClick = {
                try {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (error: ActivityNotFoundException) {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }) {
                Text("Abrir ajustes de acceso")
            }
        }
    }
}

/**
 * Pestaña de portapapeles: envío de texto al ordenador e historial bidireccional.
 */
@Composable
fun PortapapelesTab(
    uiState: AndroidConnectionUiState,
    onToggleSync: (Boolean) -> Unit,
    onSend: (String) -> Unit,
    onSendSystemClipboard: () -> Unit = {}
) {
    var draft by rememberSaveable { mutableStateOf("") }
    val tokens = UpiicsaThemeTokens.extendedColors
    TabColumn {
        SectionCard(title = "Portapapeles compartido", icon = Icons.Rounded.ContentPaste, accent = tokens.info) {
            ServiceToggleRow(
                icon = Icons.Rounded.ContentPaste,
                title = "Sincronización automática",
                subtitle = "Recibe lo que copies en el ordenador",
                checked = uiState.featureToggles.syncClipboard,
                onCheckedChange = onToggleSync,
                accent = tokens.info
            )
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("Texto para el ordenador") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    onSend(draft)
                    draft = ""
                },
                enabled = draft.isNotBlank() && uiState.status == ConnectionStatus.CONNECTED,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Enviar al ordenador")
            }
            if (uiState.useRealLink) {
                OutlinedButton(
                    onClick = onSendSystemClipboard,
                    enabled = uiState.status == ConnectionStatus.CONNECTED,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Enviar portapapeles del teléfono")
                }
                Text(
                    text = "Android 10+ solo permite leer el portapapeles en primer plano; al volver a la app o pulsar este botón se enviará tu última copia.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        SectionCard(title = "Historial", icon = Icons.Rounded.History, accent = tokens.info) {
            if (uiState.clipboardHistory.isEmpty()) {
                EmptyStatePlaceholder(Icons.Rounded.ContentPaste, "Sin elementos", "Los textos compartidos aparecerán aquí.")
            }
            uiState.clipboardHistory.forEachIndexed { index, entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                if (entry.fromMobile) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (entry.fromMobile) Icons.Rounded.PhoneAndroid else Icons.Rounded.Computer,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.text, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                        Text(
                            text = "${if (entry.fromMobile) "Enviado" else "Recibido"} · ${horaLocal(entry.epochMillis)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (index < uiState.clipboardHistory.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

/**
 * Pestaña multimedia: Music Mode hacia la PC y cámara/micrófono como periféricos para videollamadas.
 */
@Composable
fun MultimediaTab(
    uiState: AndroidConnectionUiState,
    onToggleMusic: (Boolean) -> Unit,
    onToggleCameraMic: (Boolean, Boolean) -> Unit
) {
    val connected = uiState.status == ConnectionStatus.CONNECTED
    val tokens = UpiicsaThemeTokens.extendedColors
    val music = uiState.featureToggles.musicMode
    val cameraMic = uiState.featureToggles.remoteCameraMic
    TabColumn {
        SectionCard(
            title = "Music Mode",
            subtitle = "El audio se reproduce en la tarjeta de sonido de la PC",
            icon = Icons.Rounded.MusicNote,
            accent = UpiicsaPalette.OroUpiicsa
        ) {
            ServiceToggleRow(
                icon = Icons.Rounded.MusicNote,
                title = "Enviar audio a la PC",
                subtitle = music.activeTrackTitle,
                checked = music.enabled,
                onCheckedChange = onToggleMusic,
                enabled = connected,
                accent = UpiicsaPalette.OroUpiicsa
            )
            AudioLevelMeter(level = uiState.musicModePeakLevel, seed = uiState.frameTick, height = 44.dp)
            Text(
                text = "Pico ${DisplayFormatters.percent(uiState.musicModePeakLevel)} · Ganancia ${DisplayFormatters.decimal(music.gainBoostFactor)}x",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SectionCard(
            title = "Cámara y micrófono",
            subtitle = "Usa el teléfono como webcam del portátil",
            icon = Icons.Rounded.Videocam,
            accent = tokens.warning
        ) {
            CameraPreviewPlaceholder(
                cameraEnabled = cameraMic.cameraEnabled,
                microphoneEnabled = cameraMic.microphoneEnabled,
                useBackCamera = cameraMic.useBackCamera,
                microphoneLevel = uiState.microphoneLevel,
                frameTick = uiState.frameTick
            )
            ServiceToggleRow(
                icon = Icons.Rounded.Videocam,
                title = "Transmitir cámara y micrófono",
                subtitle = cameraMic.resolutionLabel,
                checked = cameraMic.cameraEnabled,
                onCheckedChange = { enabled -> onToggleCameraMic(enabled, cameraMic.useBackCamera) },
                enabled = connected,
                accent = tokens.warning
            )
            ServiceToggleRow(
                icon = Icons.Rounded.Cameraswitch,
                title = "Cámara trasera",
                subtitle = "Mejor calidad para documentos",
                checked = cameraMic.useBackCamera,
                onCheckedChange = { back -> onToggleCameraMic(cameraMic.cameraEnabled, back) },
                enabled = connected,
                accent = tokens.warning
            )
        }
    }
}

/**
 * Pestaña de pantalla: este dispositivo como monitor secundario del ordenador con vista previa del escritorio recibido.
 */
@Composable
fun PantallaTab(
    uiState: AndroidConnectionUiState,
    onToggleDisplay: (Boolean) -> Unit
) {
    val display = uiState.featureToggles.secondaryDisplay
    val accent = UpiicsaThemeTokens.extendedColors.linkBluetooth
    TabColumn {
        SecondaryDisplayPreview(config = display, active = display.enabled, frameTick = uiState.frameTick)
        SectionCard(title = "Monitor secundario", icon = Icons.Rounded.DesktopWindows, accent = accent) {
            ServiceToggleRow(
                icon = Icons.Rounded.DesktopWindows,
                title = "Usar como pantalla extendida",
                subtitle = "${display.widthPx}×${display.heightPx} @ ${display.targetFps} fps · retorno táctil " +
                    if (display.touchInputReturnEnabled) "activo" else "inactivo",
                checked = display.enabled,
                onCheckedChange = onToggleDisplay,
                enabled = uiState.status == ConnectionStatus.CONNECTED,
                accent = accent
            )
            Text(
                text = "Gira la tablet en horizontal para aprovechar toda la superficie. Los toques y el lápiz se envían al ordenador.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
