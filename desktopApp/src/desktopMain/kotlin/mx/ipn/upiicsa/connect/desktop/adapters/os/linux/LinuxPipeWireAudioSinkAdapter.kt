package mx.ipn.upiicsa.connect.desktop.adapters.os.linux

import mx.ipn.upiicsa.connect.desktop.ports.outbound.OsAudioSinkOutboundPort

/**
 * Adaptador exclusivo de GNU/Linux para reproducir el flujo de Music Mode en la tarjeta de sonido vía PipeWire/ALSA.
 */
class LinuxPipeWireAudioSinkAdapter : OsAudioSinkOutboundPort {

    private var sinkOpen: Boolean = false
    private var configuredRateHz: Int = 48_000
    private var lastSampleCount: Int = 0

    /**
     * Abre el sumidero de audio PipeWire de baja latencia en GNU/Linux.
     */
    override suspend fun openMusicModeSink(sampleRateHz: Int): Boolean {
        // [DUMMY-PRUEBAS]: Simula apertura del stream PipeWire/ALSA en memoria para pruebas de Music Mode.
        configuredRateHz = sampleRateHz
        sinkOpen = true
        return true
    }

    /**
     * Entrega muestras PCM procesadas a la tarjeta de sonido del equipo GNU/Linux.
     */
    override suspend fun writePcmSamples(samples: ShortArray) {
        if (sinkOpen) {
            // [DUMMY-PRUEBAS]: Registra cantidad de muestras reproducidas en lugar de escribir al buffer libpipewire.
            lastSampleCount = samples.size
        }
    }

    /**
     * Cierra el sumidero de audio PipeWire al detener Music Mode.
     */
    override suspend fun closeMusicModeSink() {
        sinkOpen = false
        lastSampleCount = 0
    }
}
