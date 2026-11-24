package mx.ipn.upiicsa.connect.desktop.adapters.os.linux

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mx.ipn.upiicsa.connect.desktop.adapters.audio.JavaSoundLineOpener
import mx.ipn.upiicsa.connect.desktop.adapters.audio.JavaSoundPcmWriter
import mx.ipn.upiicsa.connect.desktop.ports.outbound.OsAudioSinkOutboundPort

/**
 * Adaptador exclusivo de GNU/Linux para reproducir el flujo de Music Mode en la tarjeta de sonido vía PipeWire/ALSA (Java Sound).
 * Sin `writer` (pruebas) o sin dispositivo de audio, contabiliza las muestras en memoria.
 */
class LinuxPipeWireAudioSinkAdapter(
    private val writer: JavaSoundPcmWriter? = null
) : OsAudioSinkOutboundPort {

    @Volatile
    private var memorySinkOpen: Boolean = false

    @Volatile
    private var memorySampleCount: Long = 0

    /**
     * Mecanismo efectivo de reproducción.
     */
    val backendLabel: String
        get() = if (writer?.isOpen == true) "Java Sound (ALSA/PipeWire)" else "Memoria (sin reproducción)"

    /**
     * Abre la salida de audio de baja latencia en GNU/Linux, preferentemente el dispositivo ALSA `default` que enruta por PipeWire.
     */
    override suspend fun openMusicModeSink(sampleRateHz: Int): Boolean {
        val opened = writer != null && withContext(Dispatchers.IO) { writer.open(sampleRateHz) }
        // [DECISIÓN TÉCNICA]: Sin tarjeta de sonido (servidores, CI) Music Mode sigue funcionando y solo se contabilizan las muestras.
        memorySinkOpen = !opened
        return opened || writer == null
    }

    /**
     * Entrega muestras PCM procesadas a la tarjeta de sonido del equipo GNU/Linux.
     */
    override suspend fun writePcmSamples(samples: ShortArray) {
        when {
            writer?.isOpen == true -> writer.write(samples)
            memorySinkOpen -> memorySampleCount += samples.size
        }
    }

    /**
     * Cierra la salida de audio al detener Music Mode.
     */
    override suspend fun closeMusicModeSink() {
        memorySinkOpen = false
        memorySampleCount = 0
        writer?.let { active -> withContext(Dispatchers.IO) { active.close() } }
    }

    companion object {

        /**
         * Construye el adaptador prefiriendo los dispositivos ALSA que enrutan por PipeWire o PulseAudio.
         */
        fun forHost(): LinuxPipeWireAudioSinkAdapter =
            LinuxPipeWireAudioSinkAdapter(JavaSoundPcmWriter(JavaSoundLineOpener(listOf("default", "pipewire", "pulse"))))
    }
}
