package mx.ipn.upiicsa.connect.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mx.ipn.upiicsa.connect.desktop.domain.DesktopSessionState
import mx.ipn.upiicsa.connect.desktop.ui.screens.ClipboardScreen
import mx.ipn.upiicsa.connect.desktop.ui.screens.DashboardScreen
import mx.ipn.upiicsa.connect.desktop.ui.screens.DevicesScreen
import mx.ipn.upiicsa.connect.desktop.ui.screens.MediaScreen
import mx.ipn.upiicsa.connect.desktop.ui.screens.NotificationsScreen
import mx.ipn.upiicsa.connect.desktop.ui.screens.SecondaryDisplayScreen
import mx.ipn.upiicsa.connect.desktop.ui.screens.SettingsScreen
import mx.ipn.upiicsa.connect.shared.model.ThemePreference
import mx.ipn.upiicsa.connect.shared.ui.components.ConnectionStatusBadge
import mx.ipn.upiicsa.connect.shared.ui.components.TransportTag
import mx.ipn.upiicsa.connect.shared.ui.components.UpiicsaBrandMark
import mx.ipn.upiicsa.connect.shared.ui.theme.UpiicsaConnectTheme

/**
 * Panel principal de escritorio (adaptador conductor): riel de navegación, barra superior de estado y pantalla activa.
 */
@Composable
fun DesktopControlPanelScreen(controller: DesktopPanelController) {
    val session by controller.session.collectAsState()
    val local by controller.local.collectAsState()

    UpiicsaConnectTheme(themePreference = local.themePreference) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Row(modifier = Modifier.fillMaxSize()) {
                RielNavegacion(
                    selected = local.section,
                    pendingNotifications = session.pendingNotificationCount,
                    onSelect = controller::selectSection
                )
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Column(modifier = Modifier.fillMaxSize()) {
                    BarraSuperiorEstado(
                        section = local.section,
                        session = session,
                        themePreference = local.themePreference,
                        onCycleTheme = { controller.setThemePreference(local.themePreference.next()) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (local.section) {
                            DesktopSection.INICIO -> DashboardScreen(controller, session, local)
                            DesktopSection.DISPOSITIVOS -> DevicesScreen(controller, session, local)
                            DesktopSection.NOTIFICACIONES -> NotificationsScreen(controller, session)
                            DesktopSection.PORTAPAPELES -> ClipboardScreen(controller, session, local)
                            DesktopSection.MULTIMEDIA -> MediaScreen(controller, session, local)
                            DesktopSection.MONITOR -> SecondaryDisplayScreen(controller, session, local)
                            DesktopSection.AJUSTES -> SettingsScreen(controller, local)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Riel lateral con el emblema y las secciones; muestra el número de notificaciones pendientes.
 */
@Composable
private fun RielNavegacion(
    selected: DesktopSection,
    pendingNotifications: Int,
    onSelect: (DesktopSection) -> Unit
) {
    NavigationRail(
        modifier = Modifier.fillMaxHeight().width(96.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        header = {
            UpiicsaBrandMark(size = 44.dp, modifier = Modifier.padding(top = 12.dp, bottom = 16.dp))
        }
    ) {
        DesktopSection.entries.forEach { section ->
            NavigationRailItem(
                selected = section == selected,
                onClick = { onSelect(section) },
                icon = {
                    if (section == DesktopSection.NOTIFICACIONES && pendingNotifications > 0) {
                        BadgedBox(badge = { Badge { Text(pendingNotifications.toString()) } }) {
                            Icon(section.icon, contentDescription = section.label)
                        }
                    } else {
                        Icon(section.icon, contentDescription = section.label)
                    }
                },
                label = { Text(section.label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                colors = NavigationRailItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

/**
 * Barra superior con el título de la sección, el estado del enlace, el medio activo y el selector de tema.
 */
@Composable
private fun BarraSuperiorEstado(
    section: DesktopSection,
    session: DesktopSessionState,
    themePreference: ThemePreference,
    onCycleTheme: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 28.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = session.statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        session.activeTransport?.let { TransportTag(it, highlighted = true) }
        ConnectionStatusBadge(session.status)
        IconButton(onClick = onCycleTheme) {
            Icon(
                imageVector = when (themePreference) {
                    ThemePreference.SYSTEM_AUTO -> Icons.Rounded.BrightnessAuto
                    ThemePreference.LIGHT -> Icons.Rounded.LightMode
                    ThemePreference.DARK -> Icons.Rounded.DarkMode
                },
                contentDescription = "Cambiar tema: ${themePreference.displayLabel}",
                tint = MaterialTheme.colorScheme.primary
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
