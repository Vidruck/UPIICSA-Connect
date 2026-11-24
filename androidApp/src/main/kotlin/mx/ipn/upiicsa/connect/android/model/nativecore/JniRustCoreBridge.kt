package mx.ipn.upiicsa.connect.android.model.nativecore

import mx.ipn.upiicsa.connect.shared.model.TransportType
import mx.ipn.upiicsa.connect.shared.protocol.DspAudioMetrics
import mx.ipn.upiicsa.connect.shared.protocol.DummyRustCoreBridge
import mx.ipn.upiicsa.connect.shared.protocol.FrameValidation
import mx.ipn.upiicsa.connect.shared.protocol.RustCoreBridgeContract
import mx.ipn.upiicsa.connect.shared.protocol.ServiceChannel
import kotlin.math.abs

/**
 * Implementación real del contrato que invoca `libupiicsa_core_rs.so` (compilada con `cargo ndk`) mediante JNI.
 */
class JniRustCoreBridge internal constructor() : RustCoreBridgeContract {

    override val isNativeBacked: Boolean = true

    /**
     * Decodifica la versión empaquetada del core nativo.
     */
    override fun coreVersion(): String {
        val packed = nativeCoreVersion()
        return "${packed shr 16 and 0xFF}.${packed shr 8 and 0xFF}.${packed and 0xFF}"
    }

    /**
     * Delegación directa a la selección de medio del core (USB -> Wi-Fi -> Bluetooth).
     */
    override fun selectBestTransport(
        usbAvailable: Boolean,
        wifiAvailable: Boolean,
        bluetoothAvailable: Boolean
    ): TransportType? = TransportType.fromWireId(nativeSelectTransport(usbAvailable, wifiAvailable, bluetoothAvailable))

    /**
     * Procesa el bloque PCM en sitio dentro del core y calcula el pico sobre las muestras ya limitadas.
     */
    override fun processMusicModePcm(samples: ShortArray, gainMultiplier: Float): DspAudioMetrics {
        if (samples.isEmpty()) {
            return DspAudioMetrics(0, 0f, 0)
        }
        val clipped = nativeProcessMusicModePcm(samples, gainMultiplier)
        val peak = samples.maxOf { abs(it.toInt()) }.toFloat() / Short.MAX_VALUE
        return DspAudioMetrics(processedSamples = samples.size, peakNormalized = peak, clippedSamples = clipped.coerceAtLeast(0))
    }

    /**
     * Calcula el CRC32 nativo de la carga útil para el canal y secuencia indicados.
     */
    override fun computeFrameChecksum(payload: ByteArray, channelId: Int, sequenceId: Long): Long =
        nativeComputeCrc32(payload, channelId, sequenceId) and 0xFFFFFFFFL

    /**
     * Serializa la trama completa en el core; falla si los identificadores o el tamaño son inválidos.
     */
    override fun encodeFrame(
        medium: TransportType,
        channel: ServiceChannel,
        sequenceId: Long,
        payload: ByteArray
    ): ByteArray = checkNotNull(nativeEncodeFrame(medium.wireId, channel.wireId, sequenceId, payload)) {
        "El core nativo rechazó la trama (medio ${medium.wireId}, canal ${channel.wireId}, ${payload.size} bytes)"
    }

    /**
     * Valida una trama serializada completa con el decodificador del core.
     */
    override fun validateWireFrame(bytes: ByteArray): FrameValidation =
        FrameValidation.fromNativeCode(nativeValidateWireFrame(bytes))

    /**
     * Deriva el PIN de emparejamiento con identificadores UTF-8.
     */
    override fun derivePairingPin(desktopId: String, mobileId: String, nonce: Long): Int =
        nativeDerivePairingPin(desktopId.encodeToByteArray(), mobileId.encodeToByteArray(), nonce)

    /**
     * Símbolo JNI `nativeCoreVersion` exportado por `core-rust/src/jni_bridge.rs`.
     */
    private external fun nativeCoreVersion(): Int

    /**
     * Símbolo JNI `nativeSelectTransport`; retorna el identificador de medio o 0.
     */
    private external fun nativeSelectTransport(usb: Boolean, wifi: Boolean, bluetooth: Boolean): Int

    /**
     * Símbolo JNI `nativeProcessMusicModePcm`; modifica `samples` en sitio y retorna las muestras recortadas.
     */
    private external fun nativeProcessMusicModePcm(samples: ShortArray, gain: Float): Int

    /**
     * Símbolo JNI `nativeComputeCrc32`.
     */
    private external fun nativeComputeCrc32(payload: ByteArray, channelId: Int, sequenceId: Long): Long

    /**
     * Símbolo JNI `nativeEncodeFrame`; retorna `null` ante argumentos inválidos.
     */
    private external fun nativeEncodeFrame(mediumId: Int, channelId: Int, sequenceId: Long, payload: ByteArray): ByteArray?

    /**
     * Símbolo JNI `nativeValidateWireFrame`.
     */
    private external fun nativeValidateWireFrame(frame: ByteArray): Int

    /**
     * Símbolo JNI `nativeDerivePairingPin`.
     */
    private external fun nativeDerivePairingPin(desktopId: ByteArray, mobileId: ByteArray, nonce: Long): Int
}

/**
 * Carga `libupiicsa_core_rs.so` empaquetada en el APK; si no está presente recurre a la simulación Kotlin.
 */
object AndroidNativeCoreLoader {

    private const val LIBRARY_NAME = "upiicsa_core_rs"

    /**
     * Devuelve el puente JNI si la biblioteca carga y responde, o `DummyRustCoreBridge` en caso contrario.
     */
    fun loadOrDummy(): RustCoreBridgeContract {
        // [DECISIÓN TÉCNICA]: Las compilaciones debug sin NDK no incluyen el .so; la app debe arrancar igual con la simulación que replica los mismos vectores.
        return try {
            System.loadLibrary(LIBRARY_NAME)
            JniRustCoreBridge().also { it.coreVersion() }
        } catch (error: UnsatisfiedLinkError) {
            DummyRustCoreBridge()
        }
    }
}
