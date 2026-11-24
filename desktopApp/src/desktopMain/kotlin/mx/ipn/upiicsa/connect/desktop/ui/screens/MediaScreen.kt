package mx.ipn.upiicsa.connect.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mx.ipn.upiicsa.connect.desktop.domain.DesktopSessionState
import mx.ipn.upiicsa.connect.desktop.ui.DesktopPanelController
import mx.ipn.upiicsa.connect.desktop.ui.DesktopPanelLocalState
import mx.ipn.upiicsa.connect.desktop.ui.ScreenColumn
import mx.ipn.upiicsa.connect.shared.model.ConnectionStatus
import mx.ipn.upiicsa.connect.shared.ui.components.AudioLevelMeter
import mx.ipn.upiicsa.connect.shared.ui.components.CameraPreviewPlaceholder
import mx.ipn.upiicsa.connect.shared.ui.components.SectionCard
import mx.ipn.upiicsa.connect.shared.ui.components.ServiceToggleRow
import mx.ipn.upiicsa.connect.shared.ui.theme.UpiicsaPalette
import mx.ipn.upiicsa.connect.shared.ui.theme.UpiicsaThemeTokens
import mx.ipn.upiicsa.connect.shared.util.DisplayFormatters

/**
 * Multimedia: Music Mode con medidor y ganancia DSP, y cámara/micrófono del teléfono como periféricos virtuales.
 */
@Composable
fun MediaScreen(
    controller: DesktopPanelController,
    session: DesktopSessionState,
    local: DesktopPanelLocalState
) {
    val connected = session.status == ConnectionStatus.CONNECTED
    val tokens = UpiicsaThemeTokens.extendedColors
    val music = session.featureToggles.musicMode
    val cameraMic = session.featureToggles.remoteCameraMic
    ScreenColumn {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            SectionCard(
                title = "Music Mode",
                subtitle = "El teléfono reproduce y la PC solo recibe audio PCM 48 kHz procesado por el DSP",
                icon = Icons.Rounded.MusicNote,
                accent = UpiicsaPalette.OroUpiicsa,
                modifier = Modifier.weight(1f)
            ) {
                ServiceToggleRow(
                    icon = Icons.Rounded.Speaker,
                    title = "Audio del teléfono a la tarjeta de sonido",
                    subtitle = if (music.enabled) music.activeTrackTitle else "Detenido",
                    checked = music.enabled,
                    onCheckedChange = controller::setMusicModeEnabled,
                    enabled = connected,
                    accent = UpiicsaPalette.OroUpiicsa
                )
                AudioLevelMeter(level = session.mediaTelemetry.musicLevel, seed = session.frameTick)
                Text(
                    text = "Nivel ${DisplayFormatters.percent(session.mediaTelemetry.musicLevel)} · " +
                        "Ganancia DSP ${DisplayFormatters.decimal(local.musicGain)}x",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = local.musicGain,
                    onValueChange = controller::updateMusicGainDraft,
                    onValueChangeFinished = controller::commitMusicGain,
                    valueRange = 0.8f..2.0f
                )
            }
            SectionCard(
                title = "Cámara y micrófono remotos",
                subtitle = "Webcam y entrada de audio virtuales para cualquier app de videollamadas",
                icon = Icons.Rounded.Videocam,
                accent = tokens.warning,
                modifier = Modifier.weight(1f)
            ) {
                CameraPreviewPlaceholder(
                    cameraEnabled = cameraMic.cameraEnabled,
                    microphoneEnabled = cameraMic.microphoneEnabled,
                    useBackCamera = cameraMic.useBackCamera,
                    microphoneLevel = session.mediaTelemetry.microphoneLevel,
                    frameTick = session.frameTick
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ServiceToggleRow(
                        icon = Icons.Rounded.Videocam,
                        title = "Cámara del teléfono",
                        subtitle = "${cameraMic.resolutionLabel} · ${DisplayFormatters.compactCount(session.mediaTelemetry.cameraFramesReceived)} cuadros",
                        checked = cameraMic.cameraEnabled,
                        onCheckedChange = controller::setCameraEnabled,
                        enabled = connected,
                        accent = tokens.warning
                    )
                    ServiceToggleRow(
                        icon = Icons.Rounded.Mic,
                        title = "Micrófono del teléfono",
                        subtitle = "Nivel ${DisplayFormatters.percent(session.mediaTelemetry.microphoneLevel)}",
                        checked = cameraMic.microphoneEnabled,
                        onCheckedChange = controller::setMicrophoneEnabled,
                        enabled = connected,
                        accent = tokens.warning
                    )
                    ServiceToggleRow(
                        icon = Icons.Rounded.Cameraswitch,
                        title = "Usar cámara trasera",
                        subtitle = "Mayor calidad de imagen para documentos y pizarrón",
                        checked = cameraMic.useBackCamera,
                        onCheckedChange = controller::setUseBackCamera,
                        enabled = connected,
                        accent = tokens.warning
                    )
                }
            }
        }
        SectionCard(
            title = "Cadena de procesamiento",
            subtitle = "Teléfono → core Rust (DSP + CRC32) → adaptador del sistema operativo",
            icon = Icons.Rounded.GraphicEq
        ) {
            Text(
                text = "Music Mode: AudioPlaybackCapture → bloque PCM → limitador suave (signal_dsp.rs) → PipeWire / WASAPI.\n" +
                    "Cámara: CameraX → cuadro codificado → v4l2loopback / MediaFoundation. Micrófono: AudioRecord → fuente virtual.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
