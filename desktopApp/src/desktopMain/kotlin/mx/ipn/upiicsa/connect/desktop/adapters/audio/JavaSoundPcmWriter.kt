package mx.ipn.upiicsa.connect.desktop.adapters.audio

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.SourceDataLine

/**
 * Salida de audio PCM abierta (16 bits con signo, little-endian, mono).
 */
interface PcmLine {

    /**
     * Escribe bytes PCM; puede bloquear mientras el búfer del dispositivo está lleno.
     */
    fun write(bytes: ByteArray)

    /**
     * Detiene y libera la salida.
     */
    fun close()
}

/**
 * Abre una [PcmLine] a la frecuencia indicada; retorna `null` si el equipo no tiene salida de audio disponible.
 */
fun interface PcmLineOpener {

    /**
     * Abre la salida de audio.
     */
    fun open(sampleRateHz: Int): PcmLine?
}

/**
 * Apertura de salidas con `javax.sound.sampled`, que usa ALSA/PipeWire en GNU/Linux y DirectSound/WASAPI en Windows.
 * Es neutral respecto al sistema operativo; cada adaptador indica qué mezcladores prefiere por nombre.
 */
class JavaSoundLineOpener(private val preferredMixerHints: List<String> = emptyList()) : PcmLineOpener {

    /**
     * Abre una `SourceDataLine` de unos 100 ms de búfer en el mezclador preferido o en el predeterminado.
     */
    override fun open(sampleRateHz: Int): PcmLine? {
        val format = AudioFormat(sampleRateHz.toFloat(), 16, 1, true, false)
        val info = DataLine.Info(SourceDataLine::class.java, format)
        return try {
            val line = preferredLine(info) ?: AudioSystem.getLine(info) as SourceDataLine
            line.open(format, sampleRateHz / 10 * format.frameSize)
            line.start()
            SourceDataLinePcmLine(line)
        } catch (error: LineUnavailableException) {
            null
        } catch (error: IllegalArgumentException) {
            null
        } catch (error: SecurityException) {
            null
        }
    }

    /**
     * Busca entre los mezcladores del sistema el primero cuyo nombre contenga alguna de las pistas y admita el formato.
     */
    private fun preferredLine(info: DataLine.Info): SourceDataLine? {
        if (preferredMixerHints.isEmpty()) {
            return null
        }
        val mixerInfo = AudioSystem.getMixerInfo().firstOrNull { candidate ->
            preferredMixerHints.any { hint -> candidate.name.contains(hint, ignoreCase = true) } &&
                AudioSystem.getMixer(candidate).isLineSupported(info)
        } ?: return null
        return AudioSystem.getMixer(mixerInfo).getLine(info) as SourceDataLine
    }
}

/**
 * [PcmLine] respaldada por una `SourceDataLine` de Java Sound ya abierta e iniciada.
 */
private class SourceDataLinePcmLine(private val line: SourceDataLine) : PcmLine {

    /**
     * Escribe los bytes en el búfer del dispositivo.
     */
    override fun write(bytes: ByteArray) {
        line.write(bytes, 0, bytes.size)
    }

    /**
     * Detiene la reproducción, descarta lo pendiente y libera el dispositivo.
     */
    override fun close() {
        line.stop()
        line.flush()
        line.close()
    }
}

/**
 * Reproduce bloques PCM de Music Mode en un hilo propio con una cola acotada: si la tarjeta de sonido se retrasa, se
 * descarta el bloque más antiguo en lugar de frenar el flujo de eventos del teléfono.
 */
class JavaSoundPcmWriter(
    private val opener: PcmLineOpener,
    private val queueCapacity: Int = DEFAULT_QUEUE_BLOCKS
) {

    private val droppedBlockCount = AtomicLong(0)

    @Volatile
    private var session: PlaybackSession? = null

    /**
     * Bloques descartados porque la cola estaba llena.
     */
    val droppedBlocks: Long
        get() = droppedBlockCount.get()

    /**
     * Indica si hay una salida de audio abierta.
     */
    val isOpen: Boolean
        get() = session != null

    /**
     * Abre la salida e inicia el hilo de reproducción; retorna `false` si no hay dispositivo de audio.
     */
    @Synchronized
    fun open(sampleRateHz: Int): Boolean {
        close()
        val line = opener.open(sampleRateHz) ?: return false
        session = PlaybackSession(line, ArrayBlockingQueue(queueCapacity)).also { it.start() }
        return true
    }

    /**
     * Encola un bloque PCM para reproducirlo; sin salida abierta no hace nada.
     */
    fun write(samples: ShortArray) {
        val active = session ?: return
        val bytes = ByteArray(samples.size * 2)
        for (index in samples.indices) {
            val value = samples[index].toInt()
            bytes[index * 2] = value.toByte()
            bytes[index * 2 + 1] = (value shr 8).toByte()
        }
        while (!active.queue.offer(bytes)) {
            if (active.queue.poll() != null) {
                droppedBlockCount.incrementAndGet()
            }
        }
    }

    /**
     * Detiene el hilo de reproducción y libera la salida.
     */
    @Synchronized
    fun close() {
        val active = session ?: return
        session = null
        active.stop()
    }

    /**
     * Hilo de reproducción ligado a una salida abierta.
     */
    private class PlaybackSession(private val line: PcmLine, val queue: ArrayBlockingQueue<ByteArray>) {

        @Volatile
        private var running = true
        private val thread = Thread(::drain, "upiicsa-music-mode").apply { isDaemon = true }

        /**
         * Inicia el hilo de reproducción.
         */
        fun start() = thread.start()

        /**
         * Señala el fin, espera brevemente al hilo y cierra la salida.
         */
        fun stop() {
            running = false
            thread.interrupt()
            thread.join(STOP_TIMEOUT_MS)
        }

        /**
         * Toma bloques de la cola y los escribe en la salida hasta que se detiene la sesión.
         */
        private fun drain() {
            try {
                while (running) {
                    val block = try {
                        queue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    } catch (error: InterruptedException) {
                        null
                    }
                    if (block != null && running) {
                        line.write(block)
                    }
                }
            } finally {
                runCatching { line.close() }
            }
        }
    }

    private companion object {
        const val DEFAULT_QUEUE_BLOCKS = 16
        const val POLL_TIMEOUT_MS = 100L
        const val STOP_TIMEOUT_MS = 1_000L
    }
}
