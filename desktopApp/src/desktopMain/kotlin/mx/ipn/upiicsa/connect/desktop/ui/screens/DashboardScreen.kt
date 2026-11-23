package mx.ipn.upiicsa.connect.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import mx.ipn.upiicsa.connect.desktop.domain.DesktopSessionState
import mx.ipn.upiicsa.connect.desktop.ui.DesktopPanelController
import mx.ipn.upiicsa.connect.desktop.ui.DesktopPanelLocalState
import mx.ipn.upiicsa.connect.desktop.ui.DesktopSection
import mx.ipn.upiicsa.connect.desktop.ui.DesktopTimeFormat
import mx.ipn.upiicsa.connect.desktop.ui.ScreenColumn
import mx.ipn.upiicsa.connect.shared.model.ConnectionStatus
import mx.ipn.upiicsa.connect.shared.ui.components.ActivityLogList
import mx.ipn.upiicsa.connect.shared.ui.components.EmptyStatePlaceholder
import mx.ipn.upiicsa.connect.shared.ui.components.MetricTile
import mx.ipn.upiicsa.connect.shared.ui.components.PairingPinDisplay
import mx.ipn.upiicsa.connect.shared.ui.components.SectionCard
import mx.ipn.upiicsa.connect.shared.ui.components.ServiceToggleRow
import mx.ipn.upiicsa.connect.shared.ui.components.UpiicsaWordmark
import mx.ipn.upiicsa.connect.shared.ui.theme.UpiicsaPalette
import mx.ipn.upiicsa.connect.shared.ui.theme.UpiicsaThemeTokens
import mx.ipn.upiicsa.connect.shared.util.DisplayFormatters

/**
 * Tablero de inicio: tarjeta institucional de la sesión, métricas del enlace, accesos rápidos a servicios y bitácora.
 */
@Composable
fun DashboardScreen(
    controller: DesktopPanelController,
    session: DesktopSessionState,
    local: DesktopPanelLocalState
) {
    val connected = session.status == ConnectionStatus.CONNECTED
    val tokens = UpiicsaThemeTokens.extendedColors
    ScreenColumn {
        TarjetaHeroeSesion(controller, session)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MetricTile(
                icon = Icons.Rounded.Speed,
                label = "Latencia del enlace",
                value = if (connected) "${session.linkTelemetry.latencyMs} ms" else "—",
                accent = tokens.info,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                icon = Icons.Rounded.SignalCellularAlt,
                label = "Calidad de señal",
                value = if (connected) "${session.linkTelemetry.signalQualityPercent} %" else "—",
                accent = tokens.success,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                icon = Icons.Rounded.SwapHoriz,
                label = "Tramas env. / recib.",
                value = "${DisplayFormatters.compactCount(session.linkTelemetry.framesSent)} / " +
                    DisplayFormatters.compactCount(session.linkTelemetry.framesReceived),
                accent = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                icon = Icons.Rounded.NotificationsActive,
                label = "Avisos pendientes",
                value = session.pendingNotificationCount.toString(),
                accent = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            SectionCard(
                title = "Servicios de continuidad",
                subtitle = if (connected) "Activa o pausa cada servicio en tiempo real" else "Conecta un dispositivo para habilitarlos",
                icon = Icons.Rounded.Tune,
                modifier = Modifier.weight(1f)
            ) {
                val toggles = session.featureToggles
                ServiceToggleRow(
                    icon = Icons.Rounded.NotificationsActive,
                    title = "Notificaciones resolubles",
                    subtitle = "Espejo y respuesta desde el ordenador",
                    checked = toggles.syncNotifications,
                    onCheckedChange = controller::setNotificationMirroring,
                    accent = MaterialTheme.colorScheme.tertiary
                )
                ServiceToggleRow(
                    icon = Icons.Rounded.ContentPaste,
                    title = "Portapapeles compartido",
                    subtitle = "Texto bidireccional PC ↔ Android",
                    checked = toggles.syncClipboard,
                    onCheckedChange = controller::setClipboardSync,
                    accent = tokens.info
                )
                ServiceToggleRow(
                    icon = Icons.Rounded.DesktopWindows,
                    title = "Monitor secundario",
                    subtitle = "${local.displayDraft.widthPx}×${local.displayDraft.heightPx} @ ${local.displayDraft.targetFps} fps",
                    checked = toggles.secondaryDisplay.enabled,
                    onCheckedChange = controller::setSecondaryDisplayEnabled,
                    enabled = connected,
                    accent = tokens.linkBluetooth
                )
                ServiceToggleRow(
                    icon = Icons.Rounded.Videocam,
                    title = "Cámara del teléfono",
                    subtitle = "Webcam virtual para videollamadas",
                    checked = toggles.remoteCameraMic.cameraEnabled,
                    onCheckedChange = controller::setCameraEnabled,
                    enabled = connected,
                    accent = tokens.warning
                )
                ServiceToggleRow(
                    icon = Icons.Rounded.MusicNote,
                    title = "Music Mode",
                    subtitle = toggles.musicMode.activeTrackTitle,
                    checked = toggles.musicMode.enabled,
                    onCheckedChange = controller::setMusicModeEnabled,
                    enabled = connected,
                    accent = UpiicsaPalette.OroUpiicsa
                )
            }
            SectionCard(
                title = "Actividad reciente",
                subtitle = "Eventos de la sesión en orden cronológico inverso",
                icon = Icons.Rounded.History,
                modifier = Modifier.weight(1f)
            ) {
                if (session.activityLog.isEmpty()) {
                    EmptyStatePlaceholder(
                        icon = Icons.Rounded.History,
                        title = "Sin actividad todavía",
                        message = "Aquí aparecerán conexiones, notificaciones y cambios de servicios."
                    )
                } else {
                    ActivityLogList(entries = session.activityLog, timeFormatter = DesktopTimeFormat::clock, maxEntries = 9)
                }
            }
        }
    }
}

/**
 * Tarjeta destacada con degradado institucional, dispositivo enlazado, PIN y accesos directos.
 */
@Composable
private fun TarjetaHeroeSesion(controller: DesktopPanelController, session: DesktopSessionState) {
    val device = session.activeDevice
    Surface(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .background(
                    Brush.linearGradient(listOf(UpiicsaPalette.VerdeUpiicsaOscuro, UpiicsaPalette.VerdeUpiicsa, Color(0xFF0B7A52)))
                )
                .padding(26.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                UpiicsaWordmark(
                    subtitle = "Escritorio ${controller.platformLabel} · UPIICSA - IPN",
                    titleColor = Color.White,
                    subtitleColor = Color.White.copy(alpha = 0.8f)
                )
                Text(
                    text = if (device != null) {
                        "${device.deviceName} enlazado (${session.direction.displayLabel})"
                    } else {
                        "Sin dispositivo enlazado"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
                Text(
                    text = if (device != null) {
                        "${device.modelName} · Android SDK ${device.androidSdkVersion ?: "N/D"} · " +
                            "vía ${session.activeTransport?.displayLabel.orEmpty()}"
                    } else {
                        "Conecta tu teléfono por cable USB (prioridad 1) o por Wi-Fi / Bluetooth (prioridad 2)."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (device == null) {
                        Button(
                            onClick = { controller.selectSection(DesktopSection.DISPOSITIVOS) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = UpiicsaPalette.OroUpiicsa,
                                contentColor = Color(0xFF231900)
                            )
                        ) {
                            Text("Buscar dispositivos")
                        }
                    } else {
                        Button(
                            onClick = controller::disconnect,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.16f),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Rounded.LinkOff, contentDescription = null)
                            Text("  Desconectar")
                        }
                    }
                }
            }
            if (session.pairingPin != null) {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("PIN de emparejamiento", style = MaterialTheme.typography.labelLarge)
                        PairingPinDisplay(pin = session.pairingPin)
                        Text(
                            text = "Debe coincidir con el del teléfono",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
