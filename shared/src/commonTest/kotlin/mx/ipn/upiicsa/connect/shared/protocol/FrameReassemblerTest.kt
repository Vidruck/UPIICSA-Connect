package mx.ipn.upiicsa.connect.shared.protocol

import mx.ipn.upiicsa.connect.shared.model.TransportType
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pruebas de paridad de [FrameReassembler] con los casos de `core-rust/src/stream_decoder.rs`.
 */
class FrameReassemblerTest {

    private val core = DummyRustCoreBridge()

    /**
     * Construye los bytes serializados de una trama de portapapeles por cable USB.
     */
    private fun trama(seq: Long, payload: String): ByteArray =
        core.encodeFrame(TransportType.USB_CABLE, ServiceChannel.CLIPBOARD_SYNC, seq, payload.encodeToByteArray())

    /**
     * Equivale a `prueba_reensamblado_byte_a_byte`: dos tramas entregadas byte por byte.
     */
    @Test
    fun reensambladoByteAByte() {
        val flujo = trama(1, "hola") + trama(2, "UPIICSA")
        val reassembler = FrameReassembler(core)
        val recibidas = mutableListOf<ReassembledFrame>()
        for (byte in flujo) {
            reassembler.feed(byteArrayOf(byte))
            recibidas += reassembler.drainValidFrames()
        }
        assertEquals(2, recibidas.size)
        assertContentEquals("UPIICSA".encodeToByteArray(), recibidas[1].payload)
        assertEquals(ServiceChannel.CLIPBOARD_SYNC, recibidas[1].channel)
        assertEquals(0, reassembler.pendingBytes)
    }

    /**
     * Equivale a `prueba_resincronizacion_con_basura`: basura antes y entre tramas válidas.
     */
    @Test
    fun resincronizacionConBasura() {
        val flujo = byteArrayOf(0x00, 0x13, 'U'.code.toByte(), 0x37) +
            trama(5, "a") +
            byteArrayOf('U'.code.toByte(), 'C'.code.toByte(), 0x09, 0x09) +
            trama(6, "b")
        val reassembler = FrameReassembler(core)
        reassembler.feed(flujo)
        val frames = reassembler.drainValidFrames()
        assertEquals(listOf(5L, 6L), frames.map { it.sequenceId })
        assertEquals(2, reassembler.stats.framesOk)
        assertTrue(reassembler.stats.framesRejected >= 1)
    }

    /**
     * Equivale a `prueba_trama_corrupta_descartada`: una trama con CRC alterado no afecta a la siguiente.
     */
    @Test
    fun tramaCorruptaDescartada() {
        val corrupta = trama(1, "dato")
        corrupta[corrupta.lastIndex] = (corrupta[corrupta.lastIndex].toInt() xor 0x01).toByte()
        val reassembler = FrameReassembler(core)
        reassembler.feed(corrupta + trama(2, "sano"))
        assertEquals(ReassemblyResult.Rejected(WireFrameError.CHECKSUM_MISMATCH), reassembler.nextFrame())
        val siguiente = assertIs<ReassemblyResult.Frame>(reassembler.nextFrame())
        assertContentEquals("sano".encodeToByteArray(), siguiente.frame.payload)
        assertNull(reassembler.nextFrame())
    }

    /**
     * Verifica que una cabecera que anuncia más de 4 MiB se rechace sin esperar la carga útil.
     */
    @Test
    fun cabeceraConCargaExcesivaRechazada() {
        val cabecera = trama(1, "x").copyOf(16)
        cabecera[8] = 0x7F
        val reassembler = FrameReassembler(core)
        reassembler.feed(cabecera)
        assertEquals(ReassemblyResult.Rejected(WireFrameError.PAYLOAD_TOO_LARGE), reassembler.nextFrame())
    }
}
