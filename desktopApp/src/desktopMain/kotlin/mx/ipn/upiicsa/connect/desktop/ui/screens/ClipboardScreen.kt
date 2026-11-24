package mx.ipn.upiicsa.connect.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ContentPasteGo
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mx.ipn.upiicsa.connect.desktop.domain.DesktopSessionState
import mx.ipn.upiicsa.connect.desktop.ui.DesktopPanelController
import mx.ipn.upiicsa.connect.desktop.ui.DesktopPanelLocalState
import mx.ipn.upiicsa.connect.desktop.ui.DesktopTimeFormat
import mx.ipn.upiicsa.connect.desktop.ui.ScreenColumn
import mx.ipn.upiicsa.connect.shared.model.ConnectionStatus
import mx.ipn.upiicsa.connect.shared.ui.components.EmptyStatePlaceholder
import mx.ipn.upiicsa.connect.shared.ui.components.SectionCard
import mx.ipn.upiicsa.connect.shared.ui.components.ServiceToggleRow
import mx.ipn.upiicsa.connect.shared.ui.theme.UpiicsaThemeTokens

/**
 * Portapapeles compartido: envío manual al teléfono, lectura del portapapeles del sistema e historial por origen.
 */
@Composable
fun ClipboardScreen(
    controller: DesktopPanelController,
    session: DesktopSessionState,
    local: DesktopPanelLocalState
) {
    val tokens = UpiicsaThemeTokens.extendedColors
    val connected = session.status == ConnectionStatus.CONNECTED
    ScreenColumn {
        SectionCard(
            title = "Enviar texto al teléfono",
            subtitle = "El texto se escribe en el portapapeles del sistema y viaja cifrado por el canal 2",
            icon = Icons.Rounded.ContentPasteGo,
            accent = tokens.info
        ) {
            ServiceToggleRow(
                icon = Icons.Rounded.ContentPaste,
                title = "Sincronización automática",
                subtitle = "Aplica en el ordenador lo que copies en el teléfono",
                checked = session.featureToggles.syncClipboard,
                onCheckedChange = controller::setClipboardSync,
                accent = tokens.info
            )
            OutlinedTextField(
                value = local.clipboardDraft,
                onValueChange = controller::updateClipboardDraft,
                label = { Text("Texto a compartir") },
                minLines = 3,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = controller::loadOsClipboardIntoDraft) {
                    Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Leer portapapeles del sistema")
                }
                Button(
                    onClick = controller::sendClipboardDraft,
                    enabled = connected && local.clipboardDraft.isNotBlank()
                ) {
                    Icon(Icons.Rounded.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Enviar al teléfono")
                }
            }
        }

        SectionCard(
            title = "Historial compartido",
            subtitle = "Últimos ${session.clipboardHistory.size} elementos sincronizados",
            icon = Icons.Rounded.History,
            accent = tokens.info
        ) {
            if (session.clipboardHistory.isEmpty()) {
                EmptyStatePlaceholder(
                    icon = Icons.Rounded.ContentPaste,
                    title = "Sin elementos",
                    message = "Lo que copies en cualquiera de los dos equipos aparecerá aquí."
                )
            }
            session.clipboardHistory.forEachIndexed { index, entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                if (entry.fromMobile) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (entry.fromMobile) Icons.Rounded.PhoneAndroid else Icons.Rounded.Computer,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.text, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                        Text(
                            text = "${if (entry.fromMobile) "Desde el teléfono" else "Desde el ordenador"} · ${DesktopTimeFormat.clock(entry.epochMillis)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (index < session.clipboardHistory.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}
