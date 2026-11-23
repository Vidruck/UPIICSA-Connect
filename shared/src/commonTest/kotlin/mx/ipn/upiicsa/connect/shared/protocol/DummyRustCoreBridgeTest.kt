package mx.ipn.upiicsa.connect.shared.protocol

import mx.ipn.upiicsa.connect.shared.model.TransportType
import mx.ipn.upiicsa.connect.shared.util.DisplayFormatters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * Pruebas de paridad entre DummyRustCoreBridge y los vectores de referencia fijados en core-rust/src/lib.rs.
 */
class DummyRustCoreBridgeTest {

    private val bridge = DummyRustCoreBridge()

    /**
     * Verifica la prioridad estricta USB -> Wi-Fi -> Bluetooth.
     */
    @Test
    fun prioridadDeTransporte() {
        assertEquals(TransportType.USB_CABLE, bridge.selectBestTransport(true, true, true))
        assertEquals(TransportType.WIFI_LAN, bridge.selectBestTransport(false, true, true))
        assertEquals(TransportType.BLUETOOTH, bridge.selectBestTransport(false, false, true))
        assertNull(bridge.selectBestTransport(false, false, false))
        assertFalse(bridge.isNativeBacked)
    }

    /**
     * Verifica los vectores CRC32 y PIN compartidos con `prueba_vectores_de_referencia_compartidos` en Rust.
     */
    @Test
    fun vectoresDeReferenciaCompartidosConRust() {
        assertEquals(2_972_122_761L, bridge.computeFrameChecksum("UPIICSA".encodeToByteArray(), 2, 7))
        assertEquals(2_353_393_211L, bridge.computeFrameChecksum(byteArrayOf(), 1, 1))
        assertEquals(178_633, bridge.derivePairingPin("pc-linux-upiicsa", "usb-pixel8-upiicsa", 42))
        assertEquals(178_633, bridge.derivePairingPin("usb-pixel8-upiicsa", "pc-linux-upiicsa", 42))
    }

    /**
     * Verifica la serialización de tramas (23 bytes para "UPIICSA") y la detección de corrupción.
     */
    @Test
    fun serializacionYValidacionDeTramas() {
        val trama = bridge.encodeFrame(TransportType.USB_CABLE, ServiceChannel.CLIPBOARD_SYNC, 7, "UPIICSA".encodeToByteArray())
        assertEquals(23, trama.size)
        assertEquals(FrameValidation.Valid(ServiceChannel.CLIPBOARD_SYNC), bridge.validateWireFrame(trama))
        trama[20] = (trama[20].toInt() xor 0x55).toByte()
        assertEquals(FrameValidation.Invalid(WireFrameError.CHECKSUM_MISMATCH), bridge.validateWireFrame(trama))
        assertEquals(FrameValidation.Invalid(WireFrameError.INCOMPLETE_HEADER), bridge.validateWireFrame(ByteArray(4)))
        assertEquals(FrameValidation.Valid(ServiceChannel.SESSION_CONTROL), FrameValidation.fromNativeCode(6))
        assertEquals(FrameValidation.Invalid(WireFrameError.CHECKSUM_MISMATCH), FrameValidation.fromNativeCode(-7))
    }

    /**
     * Verifica el DSP simulado de Music Mode (ganancia y limitador).
     */
    @Test
    fun dspMusicMode() {
        val muestras = shortArrayOf(1000, -1000, 30_000, -30_000)
        val metricas = bridge.processMusicModePcm(muestras, 1.5f)
        assertEquals(4, metricas.processedSamples)
        assertEquals(2, metricas.clippedSamples)
        assertEquals(1500, muestras[0].toInt())
    }

    /**
     * Verifica los formateadores multiplataforma usados por las interfaces.
     */
    @Test
    fun formateadores() {
        assertEquals("1.15", DisplayFormatters.decimal(1.15f))
        assertEquals("-0.50", DisplayFormatters.decimal(-0.5f))
        assertEquals("048 213", DisplayFormatters.pairingPin(48_213))
        assertEquals("73 %", DisplayFormatters.percent(0.734f))
        assertEquals("15.3 k", DisplayFormatters.compactCount(15_300))
    }
}
