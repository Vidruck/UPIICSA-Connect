package mx.ipn.upiicsa.connect.shared.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import mx.ipn.upiicsa.connect.shared.model.SecondaryDisplayConfig
import mx.ipn.upiicsa.connect.shared.ui.theme.UpiicsaPalette
import mx.ipn.upiicsa.connect.shared.ui.theme.UpiicsaThemeTokens
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Medidor de nivel de audio en barras (verde -> oro -> guinda) animado hacia el nivel normalizado recibido.
 */
@Composable
fun AudioLevelMeter(
    level: Float,
    modifier: Modifier = Modifier,
    bars: Int = 28,
    height: Dp = 56.dp,
    seed: Long = 0L
) {
    val animated by animateFloatAsState(
        targetValue = level.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 180),
        label = "nivelAudio"
    )
    val tokens = UpiicsaThemeTokens.extendedColors
    val idle = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val gap = size.width * 0.012f
        val barWidth = (size.width - gap * (bars - 1)) / bars
        for (index in 0 until bars) {
            val position = index.toFloat() / (bars - 1)
            val wave = 0.55f + 0.45f * abs(sin(index * 0.9 + seed * 0.37).toFloat())
            val envelope = 1f - abs(position - 0.5f) * 0.9f
            val barLevel = (animated * wave * envelope * 1.35f).coerceIn(0.06f, 1f)
            val lit = position <= animated + 0.02f
            val color = when {
                !lit -> idle
                position < 0.6f -> tokens.meterLow
                position < 0.85f -> tokens.meterMid
                else -> tokens.meterHigh
            }
            val barHeight = size.height * if (lit) barLevel else 0.12f
            drawRoundRect(
                color = color,
                topLeft = Offset(index * (barWidth + gap), (size.height - barHeight) / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

/**
 * Vista previa simulada del escritorio extendido enviado al monitor secundario (ventanas, barra de tareas y cursor).
 */
@Composable
fun SecondaryDisplayPreview(
    config: SecondaryDisplayConfig,
    active: Boolean,
    frameTick: Long,
    modifier: Modifier = Modifier
) {
    val tokens = UpiicsaThemeTokens.extendedColors
    val ratio = if (config.heightPx > 0) config.widthPx.toFloat() / config.heightPx else 16f / 9f
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0B0F0D))
            .padding(6.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(10.dp))
        ) {
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(tokens.previewCanvas, UpiicsaPalette.VerdeUpiicsa.copy(alpha = 0.85f)),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                )
            )
            drawArc(
                color = UpiicsaPalette.OroUpiicsa.copy(alpha = 0.35f),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(size.width * 0.55f, size.height * 0.35f),
                size = Size(size.width * 0.6f, size.height * 0.9f),
                style = Stroke(width = size.minDimension * 0.02f)
            )
            if (active) {
                drawDummyWindow(Offset(0.06f, 0.08f), Size(0.5f, 0.55f), UpiicsaPalette.VerdeUpiicsa, tokens.previewWindow)
                drawDummyWindow(Offset(0.42f, 0.3f), Size(0.48f, 0.5f), UpiicsaPalette.GuindaIpn, tokens.previewWindow)
                val t = frameTick * 0.06
                val cursor = Offset(
                    size.width * (0.5f + 0.32f * cos(t).toFloat()),
                    size.height * (0.45f + 0.25f * sin(t * 1.3).toFloat())
                )
                drawCursor(cursor, size.minDimension * 0.05f)
            } else {
                drawRect(Color.Black.copy(alpha = 0.45f))
            }
            val bar = size.height * 0.08f
            drawRect(
                color = Color.Black.copy(alpha = 0.55f),
                topLeft = Offset(0f, size.height - bar),
                size = Size(size.width, bar)
            )
            for (i in 0 until 5) {
                drawRoundRect(
                    color = if (i == 0) UpiicsaPalette.OroUpiicsa else Color.White.copy(alpha = 0.55f),
                    topLeft = Offset(size.width * 0.02f + i * bar * 1.2f, size.height - bar * 0.8f),
                    size = Size(bar * 0.8f, bar * 0.6f),
                    cornerRadius = CornerRadius(bar * 0.15f, bar * 0.15f)
                )
            }
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color.Black.copy(alpha = 0.55f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        ) {
            Text(
                text = if (active) {
                    "${config.widthPx}×${config.heightPx} · ${config.targetFps} fps"
                } else {
                    "Monitor secundario inactivo"
                },
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

/**
 * Dibuja una ventana ficticia con barra de título y líneas de contenido en coordenadas relativas.
 */
private fun DrawScope.drawDummyWindow(relativeOrigin: Offset, relativeSize: Size, titleColor: Color, body: Color) {
    val origin = Offset(size.width * relativeOrigin.x, size.height * relativeOrigin.y)
    val windowSize = Size(size.width * relativeSize.width, size.height * relativeSize.height)
    val radius = CornerRadius(size.minDimension * 0.02f, size.minDimension * 0.02f)
    drawRoundRect(color = body, topLeft = origin, size = windowSize, cornerRadius = radius)
    val titleHeight = windowSize.height * 0.14f
    drawRoundRect(color = titleColor, topLeft = origin, size = Size(windowSize.width, titleHeight), cornerRadius = radius)
    for (line in 0 until 4) {
        val y = origin.y + titleHeight * 1.8f + line * windowSize.height * 0.16f
        drawRoundRect(
            color = titleColor.copy(alpha = 0.22f),
            topLeft = Offset(origin.x + windowSize.width * 0.07f, y),
            size = Size(windowSize.width * (0.8f - line * 0.12f), windowSize.height * 0.07f),
            cornerRadius = CornerRadius(4f, 4f)
        )
    }
}

/**
 * Dibuja un puntero de ratón triangular en la posición indicada.
 */
private fun DrawScope.drawCursor(tip: Offset, length: Float) {
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(tip.x, tip.y + length)
        lineTo(tip.x + length * 0.3f, tip.y + length * 0.72f)
        lineTo(tip.x + length * 0.68f, tip.y + length * 0.7f)
        close()
    }
    drawPath(path, Color.White)
    drawPath(path, Color.Black, style = Stroke(width = length * 0.06f))
}

/**
 * Vista previa simulada de la cámara del teléfono usada como webcam, con indicadores de cámara y micrófono.
 */
@Composable
fun CameraPreviewPlaceholder(
    cameraEnabled: Boolean,
    microphoneEnabled: Boolean,
    useBackCamera: Boolean,
    microphoneLevel: Float,
    frameTick: Long,
    modifier: Modifier = Modifier
) {
    val tokens = UpiicsaThemeTokens.extendedColors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF101614))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (cameraEnabled) {
                val drift = sin(frameTick * 0.05).toFloat() * size.width * 0.01f
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color(0xFF2E4A40), Color(0xFF14201B))
                    )
                )
                drawCircle(
                    color = UpiicsaPalette.OroUpiicsaSuave.copy(alpha = 0.18f),
                    radius = size.minDimension * 0.6f,
                    center = Offset(size.width * 0.85f, size.height * 0.1f)
                )
                val center = Offset(size.width / 2f + drift, size.height * 0.42f)
                drawCircle(Color(0xFF9DB8AC), radius = size.minDimension * 0.17f, center = center)
                drawOval(
                    color = Color(0xFF9DB8AC),
                    topLeft = Offset(center.x - size.width * 0.2f, size.height * 0.66f),
                    size = Size(size.width * 0.4f, size.height * 0.5f)
                )
            }
            val corner = size.minDimension * 0.07f
            val inset = size.minDimension * 0.035f
            val stroke = Stroke(width = size.minDimension * 0.012f, cap = StrokeCap.Round)
            val frameColor = Color.White.copy(alpha = if (cameraEnabled) 0.8f else 0.25f)
            for ((x, y) in listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f)) {
                val px = if (x == 0f) inset else size.width - inset
                val py = if (y == 0f) inset else size.height - inset
                val dx = if (x == 0f) corner else -corner
                val dy = if (y == 0f) corner else -corner
                drawLine(frameColor, Offset(px, py), Offset(px + dx, py), stroke.width, StrokeCap.Round)
                drawLine(frameColor, Offset(px, py), Offset(px, py + dy), stroke.width, StrokeCap.Round)
            }
            if (!cameraEnabled) {
                val r = size.minDimension * 0.12f
                drawCircle(Color.White.copy(alpha = 0.08f), radius = r * 1.6f, center = center)
                drawArc(
                    color = Color.White.copy(alpha = 0.25f),
                    startAngle = 0f,
                    sweepAngle = (360 * (1 - 1 / PI)).toFloat(),
                    useCenter = false,
                    topLeft = Offset(center.x - r, center.y - r),
                    size = Size(r * 2, r * 2),
                    style = stroke
                )
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(horizontal = 26.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (cameraEnabled) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color(0xFFE53935), CircleShape)
                )
            }
            Text(
                text = when {
                    !cameraEnabled -> "Cámara remota apagada"
                    useBackCamera -> "EN VIVO · Cámara trasera"
                    else -> "EN VIVO · Cámara frontal"
                },
                color = Color.White,
                style = MaterialTheme.typography.labelMedium
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 26.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (cameraEnabled) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Icon(
                imageVector = if (microphoneEnabled) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                contentDescription = null,
                tint = if (microphoneEnabled) tokens.meterLow else Color.White,
                modifier = Modifier.size(18.dp)
            )
            if (microphoneEnabled) {
                AudioLevelMeter(
                    level = microphoneLevel,
                    bars = 16,
                    height = 18.dp,
                    seed = frameTick,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

