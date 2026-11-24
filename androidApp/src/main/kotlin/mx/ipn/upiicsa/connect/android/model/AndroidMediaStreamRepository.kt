package mx.ipn.upiicsa.connect.android.model

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import mx.ipn.upiicsa.connect.shared.model.MusicModeStreamConfig
import mx.ipn.upiicsa.connect.shared.model.RemoteCameraMicConfig
import mx.ipn.upiicsa.connect.shared.model.SecondaryDisplayConfig
import mx.ipn.upiicsa.connect.shared.protocol.DspAudioMetrics
import mx.ipn.upiicsa.connect.shared.protocol.RustCoreBridgeContract
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Repositorio de la capa Model (MVVM) que administra los flujos multimedia de Music Mode, Cámara/Mic y Monitor Secundario.
 */
class AndroidMediaStreamRepository(
    private val rustCore: RustCoreBridgeContract
) {

    /**
     * Inicia o detiene la captura de audio interno Android (AudioPlaybackCapture) para enviarla a la tarjeta de sonido de la PC.
     */
    suspend fun configureMusicModeCapture(config: MusicModeStreamConfig): DspAudioMetrics? {
        if (!config.enabled) {
            return null
        }
        // [DUMMY-PRUEBAS]: Genera bloque PCM de prueba en lugar de capturar buffer real desde AudioRecord/AudioPlaybackCapture.
        val syntheticPcm = shortArrayOf(1200, -1200, 2400, -2400, 3600, -3600)
        return rustCore.processMusicModePcm(syntheticPcm, config.gainBoostFactor)
    }

    /**
     * Activa o desactiva la captura de cámara y micrófono Android para exponerlos en videollamadas del ordenador.
     */
    suspend fun configureCameraMicStream(config: RemoteCameraMicConfig): RemoteCameraMicConfig {
        // [DUMMY-PRUEBAS]: Retorna configuración confirmada sin abrir sesiones CameraX ni AudioRecord en hardware.
        return config
    }

    /**
     * Prepara la superficie Android (teléfono o tablet) para recibir el escritorio extendido como monitor secundario.
     */
    suspend fun configureSecondaryDisplayReceiver(config: SecondaryDisplayConfig): SecondaryDisplayConfig {
        // [DUMMY-PRUEBAS]: Confirma estado de receptor de pantalla sin inicializar MediaCodec/SurfaceView.
        return config
    }

    /**
     * Flujo del nivel de pico de Music Mode: cada bloque PCM pasa por el DSP del core antes de enviarse a la PC.
     */
    fun musicLevelStream(gain: Float): Flow<Float> = flow {
        var tick = 0L
        while (true) {
            // [DUMMY-PRUEBAS]: Bloques PCM sintéticos (tono 440 Hz con envolvente) en lugar de AudioPlaybackCapture.
            val envelope = 0.25 + 0.55 * abs(sin(tick * 0.21))
            val block = ShortArray(SAMPLES_PER_BLOCK) { index ->
                val phase = 2 * PI * 440.0 * (tick * SAMPLES_PER_BLOCK + index) / 48_000.0
                (envelope * Short.MAX_VALUE * 0.8 * sin(phase)).roundToInt().toShort()
            }
            emit(rustCore.processMusicModePcm(block, gain).peakNormalized.coerceIn(0f, 1f))
            tick++
            delay(TICK_MS)
        }
    }

    /**
     * Flujo del nivel del micrófono capturado para la entrada virtual de videollamadas.
     */
    fun microphoneLevelStream(): Flow<Float> = flow {
        var tick = 0L
        while (true) {
            // [DUMMY-PRUEBAS]: Nivel de voz simulado con pausas en lugar de leer AudioRecord.
            val speaking = (tick / 20) % 3 != 2L
            emit(if (speaking) (0.25 + 0.45 * abs(sin(tick * 0.6))).toFloat() else 0.03f)
            tick++
            delay(TICK_MS)
        }
    }

    /**
     * Contador de cuadros recibidos/enviados que anima las vistas previas de cámara y monitor secundario.
     */
    fun frameTicks(): Flow<Long> = flow {
        var tick = 0L
        while (true) {
            // [DUMMY-PRUEBAS]: Reloj de cuadros sintético en lugar de los callbacks de MediaCodec/CameraX.
            emit(tick++)
            delay(TICK_MS)
        }
    }

    private companion object {
        const val SAMPLES_PER_BLOCK = 960
        const val TICK_MS = 120L
    }
}
