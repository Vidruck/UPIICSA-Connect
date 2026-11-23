package mx.ipn.upiicsa.connect.shared.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mx.ipn.upiicsa.connect.shared.model.ConnectionStatus
import mx.ipn.upiicsa.connect.shared.model.TransportType
import mx.ipn.upiicsa.connect.shared.ui.theme.UpiicsaMonoStyle
import mx.ipn.upiicsa.connect.shared.ui.theme.UpiicsaThemeTokens
import mx.ipn.upiicsa.connect.shared.util.DisplayFormatters

/**
 * Icono representativo de cada medio de transporte.
 */
fun TransportType.icon(): ImageVector = when (this) {
    TransportType.USB_CABLE -> Icons.Rounded.Usb
    TransportType.WIFI_LAN -> Icons.Rounded.Wifi
    TransportType.BLUETOOTH -> Icons.Rounded.Bluetooth
}

/**
 * Color de acento asociado a cada medio de transporte según el modo de tema activo.
 */
@Composable
fun TransportType.accentColor(): Color {
    val tokens = UpiicsaThemeTokens.extendedColors
    return when (this) {
        TransportType.USB_CABLE -> tokens.linkUsb
        TransportType.WIFI_LAN -> tokens.linkWifi
        TransportType.BLUETOOTH -> tokens.linkBluetooth
    }
}

/**
 * Píldora de estado de conexión con punto de color que pulsa mientras se busca o empareja.
 */
@Composable
fun ConnectionStatusBadge(status: ConnectionStatus, modifier: Modifier = Modifier) {
    val tokens = UpiicsaThemeTokens.extendedColors
    val (dotColor, container) = when (status) {
        ConnectionStatus.CONNECTED -> tokens.success to tokens.successContainer
        ConnectionStatus.SEARCHING, ConnectionStatus.PAIRING -> tokens.warning to tokens.warningContainer
        ConnectionStatus.ERROR -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer
        ConnectionStatus.DISCONNECTED -> MaterialTheme.colorScheme.outline to MaterialTheme.colorScheme.surfaceVariant
    }
    val pulsing = status == ConnectionStatus.SEARCHING || status == ConnectionStatus.PAIRING
    val transition = rememberInfiniteTransition(label = "pulsoEstado")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (pulsing) 0.3f else 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "alfaPulso"
    )
    Surface(shape = CircleShape, color = container, modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .alpha(pulse)
                    .background(dotColor, CircleShape)
            )
            Text(
                text = status.displayLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Chip seleccionable de medio de transporte con icono y rango de prioridad (1ª USB, 2ª inalámbricos).
 */
@Composable
fun TransportPriorityChip(
    transport: TransportType,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        leadingIcon = {
            Icon(
                imageVector = transport.icon(),
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else transport.accentColor(),
                modifier = Modifier.size(18.dp)
            )
        },
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(transport.shortLabel)
                Text(
                    text = "  ${transport.priorityRank}ª",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    )
}

/**
 * Indicador compacto de solo lectura con icono y nombre del medio (por ejemplo, en la lista de dispositivos).
 */
@Composable
fun TransportTag(transport: TransportType, highlighted: Boolean, modifier: Modifier = Modifier) {
    val accent = transport.accentColor()
    Row(
        modifier = modifier
            .border(1.dp, accent.copy(alpha = if (highlighted) 0.9f else 0.35f), RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = if (highlighted) 0.16f else 0f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(transport.icon(), contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
        Text(
            text = transport.shortLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Mosaico de métrica para tableros: icono con acento, valor destacado y etiqueta.
 */
@Composable
fun MetricTile(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(accent.copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            }
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Muestra un PIN de emparejamiento de 6 dígitos en casillas grandes y legibles.
 */
@Composable
fun PairingPinDisplay(pin: Int?, modifier: Modifier = Modifier) {
    val digits = pin?.let { DisplayFormatters.pairingPin(it).replace(" ", "") } ?: "------"
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        digits.forEachIndexed { index, digit ->
            if (index == 3) {
                Box(modifier = Modifier.width(8.dp))
            }
            Box(
                modifier = Modifier
                    .size(width = 38.dp, height = 48.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = digit.toString(),
                    style = MaterialTheme.typography.headlineSmall.merge(UpiicsaMonoStyle),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
