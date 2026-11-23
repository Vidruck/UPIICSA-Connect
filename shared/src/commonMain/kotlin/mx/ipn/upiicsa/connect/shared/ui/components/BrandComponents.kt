package mx.ipn.upiicsa.connect.shared.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import mx.ipn.upiicsa.connect.shared.ui.theme.UpiicsaPalette

/**
 * Emblema vectorial de UPIICSA-Connect: un ordenador y un teléfono enlazados sobre fondo verde institucional con arco oro.
 */
@Composable
fun UpiicsaBrandMark(modifier: Modifier = Modifier, size: Dp = 48.dp) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val corner = CornerRadius(s * 0.24f, s * 0.24f)
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(UpiicsaPalette.VerdeUpiicsa, UpiicsaPalette.VerdeUpiicsaOscuro),
                start = Offset.Zero,
                end = Offset(s, s)
            ),
            cornerRadius = corner
        )
        drawArc(
            color = UpiicsaPalette.OroUpiicsa,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(s * 0.14f, s * 0.12f),
            size = Size(s * 0.72f, s * 0.72f),
            style = Stroke(width = s * 0.06f, cap = StrokeCap.Round)
        )
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(s * 0.16f, s * 0.42f),
            size = Size(s * 0.38f, s * 0.26f),
            cornerRadius = CornerRadius(s * 0.04f, s * 0.04f),
            style = Stroke(width = s * 0.05f)
        )
        drawLine(
            color = Color.White,
            start = Offset(s * 0.12f, s * 0.74f),
            end = Offset(s * 0.58f, s * 0.74f),
            strokeWidth = s * 0.05f,
            cap = StrokeCap.Round
        )
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(s * 0.66f, s * 0.38f),
            size = Size(s * 0.18f, s * 0.36f),
            cornerRadius = CornerRadius(s * 0.04f, s * 0.04f),
            style = Stroke(width = s * 0.05f)
        )
        drawLine(
            color = UpiicsaPalette.OroUpiicsa,
            start = Offset(s * 0.55f, s * 0.55f),
            end = Offset(s * 0.65f, s * 0.55f),
            strokeWidth = s * 0.05f,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = UpiicsaPalette.GuindaIpnClaro,
            radius = s * 0.045f,
            center = Offset(s * 0.75f, s * 0.24f)
        )
    }
}

/**
 * Logotipo completo con emblema, nombre del producto y subtítulo institucional.
 */
@Composable
fun UpiicsaWordmark(
    subtitle: String,
    modifier: Modifier = Modifier,
    markSize: Dp = 44.dp,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UpiicsaBrandMark(size = markSize)
        Column {
            Text(
                text = "UPIICSA-Connect",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = titleColor
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = subtitleColor
            )
        }
    }
}
