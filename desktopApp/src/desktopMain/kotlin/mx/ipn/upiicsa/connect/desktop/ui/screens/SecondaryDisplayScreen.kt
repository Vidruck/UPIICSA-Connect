package mx.ipn.upiicsa.connect.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mx.ipn.upiicsa.connect.desktop.domain.DesktopSessionState
import mx.ipn.upiicsa.connect.desktop.ui.DesktopPanelController
import mx.ipn.upiicsa.connect.desktop.ui.DesktopPanelLocalState
import mx.ipn.upiicsa.connect.desktop.ui.ScreenColumn
import mx.ipn.upiicsa.connect.shared.model.ConnectionStatus
import mx.ipn.upiicsa.connect.shared.ui.components.MetricTile
import mx.ipn.upiicsa.connect.shared.ui.components.SecondaryDisplayPreview
import mx.ipn.upiicsa.connect.shared.ui.components.SectionCard
import mx.ipn.upiicsa.connect.shared.ui.components.ServiceToggleRow
import mx.ipn.upiicsa.connect.shared.ui.theme.UpiicsaThemeTokens
import mx.ipn.upiicsa.connect.shared.util.DisplayFormatters

/**
 * Resoluciones predefinidas para tablets y teléfonos usados como monitor secundario.
 */
private val PRESET_RESOLUTIONS = listOf(1280 to 800, 1920 to 1080, 2560 to 1600)

/**
 * Tasas de refresco disponibles para el monitor secundario.
 */
private val PRESET_FPS = listOf(30, 60, 90)

/**
 * Monitor secundario: vista previa del escritorio extendido, resolución, tasa de cuadros y retorno táctil.
 */
@Composable
fun SecondaryDisplayScreen(
    controller: DesktopPanelController,
    session: DesktopSessionState,
    local: DesktopPanelLocalState
) {
    val connected = session.status == ConnectionStatus.CONNECTED
    val active = session.featureToggles.secondaryDisplay.enabled
    val draft = local.displayDraft
    val accent = UpiicsaThemeTokens.extendedColors.linkBluetooth
    ScreenColumn {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            SectionCard(
                title = "Vista previa del escritorio extendido",
                subtitle = session.activeDevice?.deviceName ?: "Sin dispositivo enlazado",
                icon = Icons.Rounded.DesktopWindows,
                accent = accent,
                modifier = Modifier.weight(1.4f)
            ) {
                SecondaryDisplayPreview(config = draft, active = active, frameTick = session.frameTick)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                SectionCard(title = "Configuración", icon = Icons.Rounded.AspectRatio, accent = accent) {
                    ServiceToggleRow(
                        icon = Icons.Rounded.DesktopWindows,
                        title = "Extender pantalla",
                        subtitle = if (active) "Transmitiendo cuadros al dispositivo" else "Monitor virtual apagado",
                        checked = active,
                        onCheckedChange = controller::setSecondaryDisplayEnabled,
                        enabled = connected,
                        accent = accent
                    )
                    Text("Resolución", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PRESET_RESOLUTIONS.forEach { (w, h) ->
                            FilterChip(
                                selected = draft.widthPx == w && draft.heightPx == h,
                                onClick = { controller.updateDisplayConfig { it.copy(widthPx = w, heightPx = h) } },
                                label = { Text("$w×$h") }
                            )
                        }
                    }
                    Text("Cuadros por segundo", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PRESET_FPS.forEach { fps ->
                            FilterChip(
                                selected = draft.targetFps == fps,
                                onClick = { controller.updateDisplayConfig { it.copy(targetFps = fps) } },
                                label = { Text("$fps fps") }
                            )
                        }
                    }
                    ServiceToggleRow(
                        icon = Icons.Rounded.TouchApp,
                        title = "Retorno táctil y lápiz",
                        subtitle = "Los toques en la tablet mueven el puntero de la PC",
                        checked = draft.touchInputReturnEnabled,
                        onCheckedChange = { enabled -> controller.updateDisplayConfig { it.copy(touchInputReturnEnabled = enabled) } },
                        accent = accent
                    )
                }
                MetricTile(
                    icon = Icons.Rounded.Speed,
                    label = "Cuadros enviados al dispositivo",
                    value = DisplayFormatters.compactCount(session.mediaTelemetry.displayFramesSent),
                    accent = accent,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
