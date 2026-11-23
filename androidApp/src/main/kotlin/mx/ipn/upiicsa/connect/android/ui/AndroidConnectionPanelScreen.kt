package mx.ipn.upiicsa.connect.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.ipn.upiicsa.connect.android.viewmodel.AndroidConnectionUiState
import mx.ipn.upiicsa.connect.android.viewmodel.AndroidConnectionViewModel
import mx.ipn.upiicsa.connect.android.viewmodel.AndroidTab
import mx.ipn.upiicsa.connect.shared.model.ThemePreference
import mx.ipn.upiicsa.connect.shared.ui.components.ConnectionStatusBadge
import mx.ipn.upiicsa.connect.shared.ui.components.UpiicsaBrandMark
import mx.ipn.upiicsa.connect.shared.ui.theme.UpiicsaConnectTheme

/**
 * Icono de cada pestaña de navegación (solo capa View).
 */
private fun AndroidTab.icon(): ImageVector = when (this) {
    AndroidTab.ENLACE -> Icons.Rounded.Link
    AndroidTab.AVISOS -> Icons.Rounded.Notifications
    AndroidTab.PORTAPAPELES -> Icons.Rounded.ContentPaste
    AndroidTab.MULTIMEDIA -> Icons.Rounded.GraphicEq
    AndroidTab.PANTALLA -> Icons.Rounded.DesktopWindows
}

/**
 * Vista principal Compose en Android (capa View MVVM) que observa el StateFlow sin contener lógica de negocio.
 */
@Composable
fun AndroidConnectionPanelScreen(viewModel: AndroidConnectionViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    UpiicsaConnectTheme(themePreference = uiState.themePreference) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                EncabezadoMovilUpiicsa(
                    uiState = uiState,
                    onCycleTheme = { viewModel.setThemePreference(uiState.themePreference.next()) }
                )
            },
            bottomBar = {
                BarraPestanas(
                    selected = uiState.selectedTab,
                    pendingNotifications = uiState.pendingNotificationCount,
                    onSelect = viewModel::selectTab
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                when (uiState.selectedTab) {
                    AndroidTab.ENLACE -> EnlaceTab(
                        uiState = uiState,
                        onDirectionSelect = viewModel::setConnectionDirection,
                        onTransportSelect = viewModel::setPreferredTransport,
                        onConnect = viewModel::connectToDesktop,
                        onDisconnect = viewModel::disconnectDesktop,
                        onRescan = viewModel::cargarEstadoInicial
                    )
                    AndroidTab.AVISOS -> AvisosTab(
                        uiState = uiState,
                        onToggleBridge = viewModel::toggleNotificationBridge,
                        onSimulateResolution = viewModel::simulateResolveFromDesktop
                    )
                    AndroidTab.PORTAPAPELES -> PortapapelesTab(
                        uiState = uiState,
                        onToggleSync = viewModel::toggleClipboardSync,
                        onSend = viewModel::syncClipboard
                    )
                    AndroidTab.MULTIMEDIA -> MultimediaTab(
                        uiState = uiState,
                        onToggleMusic = viewModel::toggleMusicMode,
                        onToggleCameraMic = viewModel::toggleCameraAndMic
                    )
                    AndroidTab.PANTALLA -> PantallaTab(
                        uiState = uiState,
                        onToggleDisplay = viewModel::toggleSecondaryDisplay
                    )
                }
            }
        }
    }
}

/**
 * Encabezado con emblema, estado del enlace y selector cíclico de tema.
 */
@Composable
private fun EncabezadoMovilUpiicsa(uiState: AndroidConnectionUiState, onCycleTheme: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UpiicsaBrandMark(size = 36.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text("UPIICSA-Connect", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = uiState.statusMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                ConnectionStatusBadge(uiState.status)
                IconButton(onClick = onCycleTheme) {
                    Icon(
                        imageVector = when (uiState.themePreference) {
                            ThemePreference.SYSTEM_AUTO -> Icons.Rounded.BrightnessAuto
                            ThemePreference.LIGHT -> Icons.Rounded.LightMode
                            ThemePreference.DARK -> Icons.Rounded.DarkMode
                        },
                        contentDescription = "Cambiar tema: ${uiState.themePreference.displayLabel}",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}

/**
 * Barra inferior de pestañas con insignia de notificaciones pendientes.
 */
@Composable
private fun BarraPestanas(selected: AndroidTab, pendingNotifications: Int, onSelect: (AndroidTab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        AndroidTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = {
                    if (tab == AndroidTab.AVISOS && pendingNotifications > 0) {
                        BadgedBox(badge = { Badge { Text(pendingNotifications.toString()) } }) {
                            Icon(tab.icon(), contentDescription = tab.label)
                        }
                    } else {
                        Icon(tab.icon(), contentDescription = tab.label)
                    }
                },
                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer)
            )
        }
    }
}

/**
 * Siguiente preferencia de tema en el ciclo Automático -> Claro -> Oscuro.
 */
private fun ThemePreference.next(): ThemePreference = when (this) {
    ThemePreference.SYSTEM_AUTO -> ThemePreference.LIGHT
    ThemePreference.LIGHT -> ThemePreference.DARK
    ThemePreference.DARK -> ThemePreference.SYSTEM_AUTO
}
