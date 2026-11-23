package mx.ipn.upiicsa.connect.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mx.ipn.upiicsa.connect.desktop.ui.DesktopPanelController
import mx.ipn.upiicsa.connect.desktop.ui.DesktopPanelLocalState
import mx.ipn.upiicsa.connect.desktop.ui.ScreenColumn
import mx.ipn.upiicsa.connect.shared.model.ThemePreference
import mx.ipn.upiicsa.connect.shared.ui.components.MetricTile
import mx.ipn.upiicsa.connect.shared.ui.components.SectionCard
import mx.ipn.upiicsa.connect.shared.ui.components.UpiicsaWordmark
import mx.ipn.upiicsa.connect.shared.ui.theme.UpiicsaThemeTokens

/**
 * Ajustes: tema visual, información del núcleo nativo y datos del proyecto.
 */
@Composable
fun SettingsScreen(controller: DesktopPanelController, local: DesktopPanelLocalState) {
    val tokens = UpiicsaThemeTokens.extendedColors
    val core = controller.coreInfo
    ScreenColumn {
        SectionCard(title = "Apariencia", subtitle = "Identidad visual UPIICSA - IPN adaptable", icon = Icons.Rounded.Palette) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemePreference.entries.forEach { preference ->
                    FilterChip(
                        selected = local.themePreference == preference,
                        onClick = { controller.setThemePreference(preference) },
                        leadingIcon = {
                            Icon(
                                imageVector = when (preference) {
                                    ThemePreference.SYSTEM_AUTO -> Icons.Rounded.BrightnessAuto
                                    ThemePreference.LIGHT -> Icons.Rounded.LightMode
                                    ThemePreference.DARK -> Icons.Rounded.DarkMode
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        label = { Text(preference.displayLabel) }
                    )
                }
            }
        }
        SectionCard(title = "Núcleo nativo", subtitle = "core-rust: transporte, protocolo, DSP y CRC32", icon = Icons.Rounded.Memory) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MetricTile(
                    icon = Icons.Rounded.Memory,
                    label = "Versión del core",
                    value = core.version,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    icon = Icons.Rounded.Info,
                    label = if (core.nativeBacked) "Binario Rust cargado vía JNA" else "Simulación Kotlin (sin binario)",
                    value = if (core.nativeBacked) "Nativo" else "Simulado",
                    accent = if (core.nativeBacked) tokens.success else tokens.warning,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = "Para usar el binario nativo compile core-rust (./gradlew :desktopApp:buildRustCore) o indique la ruta con " +
                    "-Dupiicsa.core.lib.path / UPIICSA_CORE_LIB.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SectionCard(title = "Acerca de", icon = Icons.Rounded.Info) {
            UpiicsaWordmark(subtitle = "Escritorio ${controller.platformLabel} · Versión 1.0.0")
            Text(
                text = "Plataforma de convergencia entre equipos de escritorio Windows / GNU/Linux y dispositivos Android. " +
                    "Todos los derechos reservados al equipo desarrollador.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
