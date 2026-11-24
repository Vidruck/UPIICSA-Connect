package mx.ipn.upiicsa.connect.desktop.adapters

import kotlinx.coroutines.runBlocking
import mx.ipn.upiicsa.connect.desktop.adapters.audio.JavaSoundLineOpener
import mx.ipn.upiicsa.connect.desktop.adapters.audio.JavaSoundPcmWriter
import mx.ipn.upiicsa.connect.desktop.adapters.audio.PcmLine
import mx.ipn.upiicsa.connect.desktop.adapters.os.linux.LinuxPipeWireAudioSinkAdapter
import mx.ipn.upiicsa.connect.desktop.adapters.os.windows.WindowsWasapiAudioSinkAdapter
import org.junit.Assume
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Pruebas de la reproducción de Music Mode con una salida de audio falsa y, si se habilita, con la tarjeta de sonido real.
 */
class JavaSoundPcmWriterTest {

    /**
     * Salida de audio falsa que entrega cada bloque a una cola y puede bloquearse para simular un dispositivo lento.
     */
    private class FakeLine(private val gate: CountDownLatch = CountDownLatch(0)) : PcmLine {
        val written = LinkedBlockingQueue<ByteArray>()

        @Volatile
        var closed = false

        /**
         * Espera a que se abra la compuerta y registra el bloque.
         */
        override fun write(bytes: ByteArray) {
            gate.await(5, TimeUnit.SECONDS)
            written.put(bytes)
        }

        /**
         * Marca la salida como cerrada.
         */
        override fun close() {
            closed = true
        }
    }

    /**
     * Verifica la conversión a PCM 16 bits little-endian y el cierre de la salida.
     */
    @Test
    fun convierteALittleEndianYCierra() {
        val line = FakeLine()
        val writer = JavaSoundPcmWriter({ line })
        assertTrue(writer.open(48_000))
        writer.write(shortArrayOf(1, -2, 0x1234))
        val block = assertNotNull(line.written.poll(2, TimeUnit.SECONDS))
        assertContentEquals(byteArrayOf(1, 0, -2, -1, 0x34, 0x12), block)
        writer.close()
        assertTrue(line.closed)
        assertFalse(writer.isOpen)
    }

    /**
     * Verifica que con un dispositivo lento se descarten los bloques más antiguos sin bloquear a quien escribe.
     */
    @Test
    fun descartaBloquesAntiguosSinBloquear() {
        val gate = CountDownLatch(1)
        val line = FakeLine(gate)
        val writer = JavaSoundPcmWriter({ line }, queueCapacity = 2)
        writer.open(48_000)
        repeat(10) { index -> writer.write(shortArrayOf(index.toShort())) }
        assertTrue(writer.droppedBlocks >= 7)
        gate.countDown()
        val lastValues = generateSequence { line.written.poll(500, TimeUnit.MILLISECONDS) }.map { it[0].toInt() }.toList()
        assertEquals(9, lastValues.last())
        writer.close()
    }

    /**
     * Verifica que sin dispositivo de audio los adaptadores sigan funcionando en memoria.
     */
    @Test
    fun sinDispositivoLosAdaptadoresUsanMemoria() = runBlocking {
        val linux = LinuxPipeWireAudioSinkAdapter(JavaSoundPcmWriter({ null }))
        assertFalse(linux.openMusicModeSink(48_000))
        linux.writePcmSamples(shortArrayOf(1, 2))
        linux.closeMusicModeSink()
        assertTrue(WindowsWasapiAudioSinkAdapter().openMusicModeSink(48_000))
        assertEquals("Memoria (sin reproducción)", linux.backendLabel)
    }

    /**
     * Abre la tarjeta de sonido real y reproduce 100 ms de silencio (solo con la variable de pruebas del sistema real).
     */
    @Test
    fun tarjetaDeSonidoRealReproduceSilencio() {
        OsIntegrationTestSupport.assumeRealOsTestsEnabled()
        val hints = if (OsIntegrationTestSupport.isWindows) emptyList() else listOf("default", "pipewire", "pulse")
        val writer = JavaSoundPcmWriter(JavaSoundLineOpener(hints))
        Assume.assumeTrue("Sin dispositivo de audio", writer.open(48_000))
        repeat(5) { writer.write(ShortArray(960)) }
        Thread.sleep(200)
        writer.close()
        assertFalse(writer.isOpen)
    }
}
