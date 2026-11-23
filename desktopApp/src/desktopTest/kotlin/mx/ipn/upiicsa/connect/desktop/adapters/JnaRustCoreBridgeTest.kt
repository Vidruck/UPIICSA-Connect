package mx.ipn.upiicsa.connect.desktop.adapters

import mx.ipn.upiicsa.connect.desktop.adapters.nativecore.NativeCoreLoader
import mx.ipn.upiicsa.connect.shared.model.TransportType
import mx.ipn.upiicsa.connect.shared.protocol.DummyRustCoreBridge
import mx.ipn.upiicsa.connect.shared.protocol.FrameValidation
import mx.ipn.upiicsa.connect.shared.protocol.ServiceChannel
import mx.ipn.upiicsa.connect.shared.protocol.WireFrameError
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pruebas del puente JNA contra el binario real de core-rust; se omiten si el binario no fue compilado.
 */
class JnaRustCoreBridgeTest {

    /**
     * Verifica que el binario nativo produzca los mismos resultados que la simulación Kotlin.
     */
    @Test
    fun paridadEntreBinarioNativoYSimulacion() {
        val native = NativeCoreLoader.loadNativeOrNull()
        if (native == null) {
            println("[JnaRustCoreBridgeTest] Binario core-rust no disponible: ejecute ./gradlew :desktopApp:buildRustCore")
            return
        }
        val dummy = DummyRustCoreBridge()
        assertTrue(native.isNativeBacked)
        assertEquals("0.2.0", native.coreVersion())
        assertEquals(TransportType.USB_CABLE, native.selectBestTransport(true, true, false))
        assertEquals(TransportType.BLUETOOTH, native.selectBestTransport(false, false, true))
        assertEquals(2_972_122_761L, native.computeFrameChecksum("UPIICSA".encodeToByteArray(), 2, 7))
        assertEquals(dummy.computeFrameChecksum(byteArrayOf(), 1, 1), native.computeFrameChecksum(byteArrayOf(), 1, 1))
        assertEquals(178_633, native.derivePairingPin("pc-linux-upiicsa", "usb-pixel8-upiicsa", 42))

        val payload = "trama de prueba".encodeToByteArray()
        val nativeFrame = native.encodeFrame(TransportType.WIFI_LAN, ServiceChannel.MUSIC_MODE_AUDIO, 99, payload)
        assertContentEquals(dummy.encodeFrame(TransportType.WIFI_LAN, ServiceChannel.MUSIC_MODE_AUDIO, 99, payload), nativeFrame)
        assertEquals(FrameValidation.Valid(ServiceChannel.MUSIC_MODE_AUDIO), native.validateWireFrame(nativeFrame))
        nativeFrame[nativeFrame.lastIndex] = 0
        assertEquals(FrameValidation.Invalid(WireFrameError.CHECKSUM_MISMATCH), native.validateWireFrame(nativeFrame))

        val nativeSamples = shortArrayOf(1000, -1000, 30_000, -30_000)
        val dummySamples = nativeSamples.copyOf()
        val nativeMetrics = native.processMusicModePcm(nativeSamples, 1.5f)
        dummy.processMusicModePcm(dummySamples, 1.5f)
        assertContentEquals(dummySamples, nativeSamples)
        assertEquals(2, nativeMetrics.clippedSamples)
    }
}
