package mx.ipn.upiicsa.connect.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsPaused
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mx.ipn.upiicsa.connect.desktop.domain.DesktopSessionState
import mx.ipn.upiicsa.connect.desktop.ui.DesktopPanelController
import mx.ipn.upiicsa.connect.desktop.ui.ScreenColumn
import mx.ipn.upiicsa.connect.shared.model.ConnectionStatus
import mx.ipn.upiicsa.connect.shared.ui.components.EmptyStatePlaceholder
import mx.ipn.upiicsa.connect.shared.ui.components.MetricTile
import mx.ipn.upiicsa.connect.shared.ui.components.NotificationResolutionCard
import mx.ipn.upiicsa.connect.shared.ui.components.SectionCard
import mx.ipn.upiicsa.connect.shared.ui.components.ServiceToggleRow
import mx.ipn.upiicsa.connect.shared.ui.theme.UpiicsaThemeTokens

/**
 * Bandeja de notificaciones del teléfono resolubles desde el ordenador (responder, marcar leída o descartar).
 */
@Composable
fun NotificationsScreen(controller: DesktopPanelController, session: DesktopSessionState) {
    val pending = session.notifications.filterNot { it.isResolved }
    val resolved = session.notifications.filter { it.isResolved }
    val connected = session.status == ConnectionStatus.CONNECTED
    ScreenColumn {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MetricTile(
                icon = Icons.Rounded.NotificationsActive,
                label = "Pendientes",
                value = pending.size.toString(),
                accent = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                icon = Icons.Rounded.CheckCircle,
                label = "Resueltas desde la PC",
                value = resolved.size.toString(),
                accent = UpiicsaThemeTokens.extendedColors.success,
                modifier = Modifier.weight(1f)
            )
        }
        SectionCard(title = "Espejo de notificaciones", icon = Icons.Rounded.NotificationsActive, accent = MaterialTheme.colorScheme.tertiary) {
            ServiceToggleRow(
                icon = if (session.featureToggles.syncNotifications) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsPaused,
                title = "Recibir notificaciones del teléfono",
                subtitle = "Se publican también en el centro de notificaciones del sistema operativo",
                checked = session.featureToggles.syncNotifications,
                onCheckedChange = controller::setNotificationMirroring,
                accent = MaterialTheme.colorScheme.tertiary
            )
        }
        if (session.notifications.isEmpty()) {
            EmptyStatePlaceholder(
                icon = Icons.Rounded.Inbox,
                title = "Bandeja vacía",
                message = if (connected) "Las notificaciones nuevas del teléfono aparecerán aquí." else "Conecta un dispositivo para espejar sus notificaciones."
            )
        }
        if (pending.isNotEmpty()) {
            Text("Por resolver", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            pending.forEach { item ->
                NotificationResolutionCard(notification = item, onResolve = controller::resolveNotification, actionsEnabled = connected)
            }
        }
        if (resolved.isNotEmpty()) {
            Text("Resueltas", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            resolved.forEach { item ->
                NotificationResolutionCard(notification = item, onResolve = controller::resolveNotification)
            }
        }
    }
}
