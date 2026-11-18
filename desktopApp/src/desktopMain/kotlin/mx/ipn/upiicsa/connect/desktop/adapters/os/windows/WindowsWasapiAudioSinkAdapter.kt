package mx.ipn.upiicsa.connect.desktop.adapters.os.windows

import mx.ipn.upiicsa.connect.desktop.ports.outbound.OsAudioSinkOutboundPort

/**
 * Adaptador exclusivo de Windows para enviar el audio de Music Mode hacia la tarjeta de sonido mediante WASAPI.
 */
class WindowsWasapiAudioSinkAdapter : OsAudioSinkOutboundPort {

    private var sinkOpen: Boolean = false
    private var configuredRateHz: Int = 48_000
    private var renderedSamples: Int = 0

    /**
     * Inicializa el cliente de renderizado de audio WASAPI en Windows.
     */
    override suspend fun openMusicModeSink(sampleRateHz: Int): Boolean {
        // [DUMMY-PRUEBAS]: Simula apertura de IAudioClient / IAudioRenderClient de WASAPI en memoria.
        configuredRateHz = sampleRateHz
        sinkOpen = true
        return true
    }

    /**
     * Escribe muestras PCM procesadas en el buffer de salida de la tarjeta de sonido de Windows.
     */
    override suspend fun writePcmSamples(samples: ShortArray) {
        if (sinkOpen) {
            // [DUMMY-PRUEBAS]: Registra muestras entregadas sin abrir flujo COM nativo de Windows.
            renderedSamples = samples.size
        }
    }

    /**
     * Cierra el flujo WASAPI al desactivar Music Mode.
     */
    override suspend fun closeMusicModeSink() {
        sinkOpen = false
        renderedSamples = 0
    }
}
