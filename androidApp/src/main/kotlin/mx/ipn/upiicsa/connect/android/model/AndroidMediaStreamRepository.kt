package mx.ipn.upiicsa.connect.android.model

import mx.ipn.upiicsa.connect.shared.model.MusicModeStreamConfig
import mx.ipn.upiicsa.connect.shared.model.RemoteCameraMicConfig
import mx.ipn.upiicsa.connect.shared.model.SecondaryDisplayConfig
import mx.ipn.upiicsa.connect.shared.protocol.DspAudioMetrics
import mx.ipn.upiicsa.connect.shared.protocol.RustCoreBridgeContract

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
}
