package mx.ipn.upiicsa.connect.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mx.ipn.upiicsa.connect.shared.model.NotificationResolutionAction
import mx.ipn.upiicsa.connect.shared.model.PhoneNotificationItem
import mx.ipn.upiicsa.connect.shared.ui.theme.UpiicsaPalette
import mx.ipn.upiicsa.connect.shared.ui.theme.UpiicsaThemeTokens

/**
 * Color estable de avatar derivado del paquete de la app emisora.
 */
private fun avatarColorFor(packageName: String): Color {
    val palette = listOf(
        UpiicsaPalette.VerdeUpiicsa,
        UpiicsaPalette.GuindaIpn,
        Color(0xFF1F5FA8),
        Color(0xFF8A5A00),
        Color(0xFF5B4BB7),
        Color(0xFF00796B)
    )
    val index = packageName.fold(0) { acc, c -> (acc * 31 + c.code) and 0x7FFFFFFF } % palette.size
    return palette[index]
}

/**
 * Tarjeta de notificación móvil con avatar, contenido y acciones de resolución (responder, marcar leída, descartar).
 */
@Composable
fun NotificationResolutionCard(
    notification: PhoneNotificationItem,
    onResolve: (NotificationResolutionAction) -> Unit,
    modifier: Modifier = Modifier,
    actionsEnabled: Boolean = true,
    replyPlaceholder: String = "Responder desde el ordenador"
) {
    var quickReply by remember(notification.notificationId) { mutableStateOf("") }
    val tokens = UpiicsaThemeTokens.extendedColors

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (notification.isResolved) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (notification.isResolved) 0.dp else 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(avatarColorFor(notification.packageName), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = notification.appName.take(1).uppercase(),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = notification.senderTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${notification.appName} · ${notification.timestampLabel}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (notification.isResolved) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Resuelta",
                        tint = tokens.success
                    )
                }
            }

            Text(
                text = notification.messageBody,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (notification.isResolved) {
                Text(
                    text = notification.resolutionSummary.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    color = tokens.success
                )
            } else if (actionsEnabled) {
                if (notification.supportsInlineReply) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = quickReply,
                            onValueChange = { quickReply = it },
                            placeholder = { Text(replyPlaceholder) },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.weight(1f)
                        )
                        FilledIconButton(
                            enabled = quickReply.isNotBlank(),
                            onClick = {
                                onResolve(NotificationResolutionAction.QuickReply(notification.notificationId, quickReply))
                                quickReply = ""
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Enviar respuesta")
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onResolve(NotificationResolutionAction.MarkAsRead(notification.notificationId)) }
                    ) {
                        Icon(Icons.Rounded.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  Marcar leída")
                    }
                    OutlinedButton(
                        onClick = { onResolve(NotificationResolutionAction.Dismiss(notification.notificationId)) }
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  Descartar")
                    }
                }
            }
        }
    }
}
