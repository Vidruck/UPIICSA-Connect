package mx.ipn.upiicsa.connect.desktop.adapters.nativecore

import com.sun.jna.IntegerType
import com.sun.jna.Library
import com.sun.jna.Native
import mx.ipn.upiicsa.connect.shared.model.TransportType
import mx.ipn.upiicsa.connect.shared.protocol.DspAudioMetrics
import mx.ipn.upiicsa.connect.shared.protocol.DummyRustCoreBridge
import mx.ipn.upiicsa.connect.shared.protocol.FrameValidation
import mx.ipn.upiicsa.connect.shared.protocol.RustCoreBridgeContract
import mx.ipn.upiicsa.connect.shared.protocol.ServiceChannel
import java.io.File
import kotlin.math.abs

/**
 * Tipo `size_t` nativo con el ancho correcto de la plataforma (64 bits en Windows y GNU/Linux x86_64).
 */
class SizeT(value: Long = 0L) : IntegerType(Native.SIZE_T_SIZE, value, true) {

    /**
     * Constructor sin argumentos requerido por JNA para instanciar el tipo por reflexión.
     */
    constructor() : this(0L)

    /**
     * Conversión a byte requerida por la jerarquía Number de Kotlin.
     */
    override fun toByte(): Byte = toLong().toByte()

    /**
     * Conversión a short requerida por la jerarquía Number de Kotlin.
     */
    override fun toShort(): Short = toLong().toShort()
}

/**
 * Firma JNA de las funciones C-ABI exportadas por core-rust/src/ffi_bridge.rs.
 */
@Suppress("FunctionName")
interface UpiicsaCoreLibrary : Library {

    /**
     * Versión empaquetada (mayor << 16 | menor << 8 | parche).
     */
    fun upiicsa_core_version(): Int

    /**
     * Selección de medio por prioridad (1 USB, 2 Wi-Fi, 3 Bluetooth, 0 ninguno).
     */
    fun upiicsa_select_transport_medium(usb: Byte, wifi: Byte, bluetooth: Byte): Byte

    /**
     * DSP de Music Mode en sitio; retorna muestras recortadas o -1.
     */
    fun upiicsa_process_music_mode_pcm(samples: ShortArray, count: SizeT, gain: Float): Int

    /**
     * CRC32 de la carga útil con canal y secuencia.
     */
    fun upiicsa_compute_frame_crc32(payload: ByteArray, length: SizeT, channelId: Byte, sequenceId: Int): Int

    /**
     * Serializa una trama completa en el búfer de salida; retorna bytes escritos o código negativo.
     */
    fun upiicsa_encode_frame(
        mediumId: Byte,
        channelId: Byte,
        sequenceId: Int,
        payload: ByteArray,
        payloadLength: SizeT,
        out: ByteArray,
        outCapacity: SizeT
    ): Long

    /**
     * Valida una trama; retorna el canal (1..6) o un código negativo de error.
     */
    fun upiicsa_validate_wire_frame(bytes: ByteArray, length: SizeT): Int

    /**
     * PIN de emparejamiento de 6 dígitos.
     */
    fun upiicsa_derive_pairing_pin(
        desktopId: ByteArray,
        desktopLength: SizeT,
        mobileId: ByteArray,
        mobileLength: SizeT,
        nonce: Int
    ): Int
}

/**
 * Implementación real del contrato que invoca la biblioteca compartida `upiicsa_core_rs` (.so / .dll) mediante JNA.
 */
class JnaRustCoreBridge(private val library: UpiicsaCoreLibrary) : RustCoreBridgeContract {

    override val isNativeBacked: Boolean = true

    /**
     * Decodifica la versión empaquetada del core nativo.
     */
    override fun coreVersion(): String {
        val packed = library.upiicsa_core_version()
        return "${packed shr 16 and 0xFF}.${packed shr 8 and 0xFF}.${packed and 0xFF}"
    }

    /**
     * Delegación directa a `upiicsa_select_transport_medium`.
     */
    override fun selectBestTransport(
        usbAvailable: Boolean,
        wifiAvailable: Boolean,
        bluetoothAvailable: Boolean
    ): TransportType? {
        val medium = library.upiicsa_select_transport_medium(
            usbAvailable.toByte(),
            wifiAvailable.toByte(),
            bluetoothAvailable.toByte()
        )
        return TransportType.fromWireId(medium.toInt())
    }

    /**
     * Procesa el bloque en el core nativo; el pico se calcula sobre las muestras ya limitadas que devuelve JNA.
     */
    override fun processMusicModePcm(samples: ShortArray, gainMultiplier: Float): DspAudioMetrics {
        if (samples.isEmpty()) {
            return DspAudioMetrics(0, 0f, 0)
        }
        val clipped = library.upiicsa_process_music_mode_pcm(samples, SizeT(samples.size.toLong()), gainMultiplier)
        val peak = samples.maxOf { abs(it.toInt()) }.toFloat() / Short.MAX_VALUE
        return DspAudioMetrics(processedSamples = samples.size, peakNormalized = peak, clippedSamples = clipped.coerceAtLeast(0))
    }

    /**
     * Delegación a `upiicsa_compute_frame_crc32` interpretando el resultado como entero sin signo.
     */
    override fun computeFrameChecksum(payload: ByteArray, channelId: Int, sequenceId: Long): Long {
        val crc = library.upiicsa_compute_frame_crc32(
            payload.orPlaceholder(),
            SizeT(payload.size.toLong()),
            channelId.toByte(),
            sequenceId.toInt()
        )
        return crc.toLong() and 0xFFFFFFFFL
    }

    /**
     * Delegación a `upiicsa_encode_frame` con un búfer de salida dimensionado exactamente.
     */
    override fun encodeFrame(
        medium: TransportType,
        channel: ServiceChannel,
        sequenceId: Long,
        payload: ByteArray
    ): ByteArray {
        val out = ByteArray(FRAME_HEADER_LEN + payload.size)
        val written = library.upiicsa_encode_frame(
            medium.wireId.toByte(),
            channel.wireId.toByte(),
            sequenceId.toInt(),
            payload.orPlaceholder(),
            SizeT(payload.size.toLong()),
            out,
            SizeT(out.size.toLong())
        )
        check(written >= 0) { "upiicsa_encode_frame devolvió el código $written" }
        return out.copyOf(written.toInt())
    }

    /**
     * Delegación a `upiicsa_validate_wire_frame`.
     */
    override fun validateWireFrame(bytes: ByteArray): FrameValidation {
        val code = library.upiicsa_validate_wire_frame(bytes.orPlaceholder(), SizeT(bytes.size.toLong()))
        return FrameValidation.fromNativeCode(code)
    }

    /**
     * Delegación a `upiicsa_derive_pairing_pin` con identificadores UTF-8.
     */
    override fun derivePairingPin(desktopId: String, mobileId: String, nonce: Long): Int {
        val desktop = desktopId.encodeToByteArray()
        val mobile = mobileId.encodeToByteArray()
        return library.upiicsa_derive_pairing_pin(
            desktop.orPlaceholder(),
            SizeT(desktop.size.toLong()),
            mobile.orPlaceholder(),
            SizeT(mobile.size.toLong()),
            nonce.toInt()
        )
    }

    private companion object {
        const val FRAME_HEADER_LEN = 16

        /**
         * Convierte un booleano en la bandera u8 esperada por la interfaz C.
         */
        fun Boolean.toByte(): Byte = if (this) 1 else 0

        /**
         * Evita pasar arreglos vacíos a JNA (que los traduce a puntero nulo); el core ignora el contenido cuando la longitud es 0.
         */
        fun ByteArray.orPlaceholder(): ByteArray = if (isEmpty()) ByteArray(1) else this
    }
}

/**
 * Localiza y carga el binario de core-rust; si no está disponible recurre a la simulación Kotlin sin interrumpir la app.
 */
object NativeCoreLoader {

    /**
     * Propiedad del sistema con la ruta absoluta del binario (por ejemplo, `core-rust/target/release/libupiicsa_core_rs.so`).
     */
    const val LIBRARY_PATH_PROPERTY = "upiicsa.core.lib.path"

    /**
     * Variable de entorno alternativa con la ruta absoluta del binario.
     */
    const val LIBRARY_PATH_ENV = "UPIICSA_CORE_LIB"

    private const val LIBRARY_NAME = "upiicsa_core_rs"

    /**
     * Intenta cargar el binario nativo y devuelve `null` si no se encuentra o no es compatible.
     */
    fun loadNativeOrNull(): RustCoreBridgeContract? {
        val explicitPath = System.getProperty(LIBRARY_PATH_PROPERTY) ?: System.getenv(LIBRARY_PATH_ENV)
        val target = explicitPath?.takeIf { File(it).isFile } ?: LIBRARY_NAME
        return try {
            JnaRustCoreBridge(Native.load(target, UpiicsaCoreLibrary::class.java))
        } catch (error: UnsatisfiedLinkError) {
            null
        }
    }

    /**
     * Devuelve el puente nativo si está disponible o, en su defecto, `DummyRustCoreBridge`.
     */
    fun loadOrDummy(): RustCoreBridgeContract {
        // [DECISIÓN TÉCNICA]: El escritorio debe arrancar aun sin compilar core-rust; la simulación Kotlin replica los mismos vectores.
        return loadNativeOrNull() ?: DummyRustCoreBridge()
    }
}
