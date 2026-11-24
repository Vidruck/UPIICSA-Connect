package mx.ipn.upiicsa.connect.desktop.adapters.os.windows

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mx.ipn.upiicsa.connect.desktop.adapters.audio.JavaSoundLineOpener
import mx.ipn.upiicsa.connect.desktop.adapters.audio.JavaSoundPcmWriter
import mx.ipn.upiicsa.connect.desktop.ports.outbound.OsAudioSinkOutboundPort

/**
 * Adaptador exclusivo de Windows para enviar el audio de Music Mode a la tarjeta de sonido (Java Sound sobre DirectSound/WASAPI).
 * Sin `writer` (pruebas) o sin dispositivo de audio, contabiliza las muestras en memoria.
 */
class WindowsWasapiAudioSinkAdapter(
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
        get() = if (writer?.isOpen == true) "Java Sound (DirectSound/WASAPI)" else "Memoria (sin reproducción)"

    /**
     * Abre la salida de audio predeterminada de Windows.
     */
    override suspend fun openMusicModeSink(sampleRateHz: Int): Boolean {
        val opened = writer != null && withContext(Dispatchers.IO) { writer.open(sampleRateHz) }
        // [DECISIÓN TÉCNICA]: Sin tarjeta de sonido (servidores, CI) Music Mode sigue funcionando y solo se contabilizan las muestras.
        memorySinkOpen = !opened
        return opened || writer == null
    }

    /**
     * Envía muestras PCM procesadas a la tarjeta de sonido de Windows.
     */
    override suspend fun writePcmSamples(samples: ShortArray) {
        when {
            writer?.isOpen == true -> writer.write(samples)
            memorySinkOpen -> memorySampleCount += samples.size
        }
    }

    /**
     * Libera la salida de audio al detener Music Mode.
     */
    override suspend fun closeMusicModeSink() {
        memorySinkOpen = false
        memorySampleCount = 0
        writer?.let { active -> withContext(Dispatchers.IO) { active.close() } }
    }

    companion object {

        /**
         * Construye el adaptador con el dispositivo de reproducción predeterminado de Windows.
         */
        fun forHost(): WindowsWasapiAudioSinkAdapter =
            WindowsWasapiAudioSinkAdapter(JavaSoundPcmWriter(JavaSoundLineOpener()))
    }
}
