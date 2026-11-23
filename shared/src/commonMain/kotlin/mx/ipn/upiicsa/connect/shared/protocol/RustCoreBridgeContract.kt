package mx.ipn.upiicsa.connect.shared.protocol

import mx.ipn.upiicsa.connect.shared.model.TransportType
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Define el contrato de interoperabilidad entre los módulos Kotlin Multiplatform y el núcleo nativo en Rust.
 */
interface RustCoreBridgeContract {

    /**
     * Indica si la implementación está respaldada por el binario nativo de core-rust (`true`) o es una simulación (`false`).
     */
    val isNativeBacked: Boolean

    /**
     * Versión semántica del núcleo (por ejemplo, "0.2.0").
     */
    fun coreVersion(): String

    /**
     * Determina el mejor medio físico disponible respetando cable USB como prioridad 1 y enlaces inalámbricos como prioridad 2.
     */
    fun selectBestTransport(
        usbAvailable: Boolean,
        wifiAvailable: Boolean,
        bluetoothAvailable: Boolean
    ): TransportType?

    /**
     * Aplica el algoritmo de ganancia y limitación suave de Music Mode sobre muestras PCM de 16 bits.
     */
    fun processMusicModePcm(samples: ShortArray, gainMultiplier: Float): DspAudioMetrics

    /**
     * Calcula el código de verificación CRC32 sobre una carga útil de protocolo para garantizar integridad.
     */
    fun computeFrameChecksum(payload: ByteArray, channelId: Int, sequenceId: Long): Long

    /**
     * Serializa una trama completa (cabecera de 16 bytes + carga útil) lista para enviarse por el medio indicado.
     */
    fun encodeFrame(medium: TransportType, channel: ServiceChannel, sequenceId: Long, payload: ByteArray): ByteArray

    /**
     * Valida firma, longitud y CRC32 de una trama serializada recibida.
     */
    fun validateWireFrame(bytes: ByteArray): FrameValidation

    /**
     * Deriva el PIN de emparejamiento de 6 dígitos que ambos equipos deben mostrar para confirmar el enlace.
     */
    fun derivePairingPin(desktopId: String, mobileId: String, nonce: Long): Int
}

/**
 * Encapsula métricas de procesamiento DSP devueltas por el núcleo de audio de Music Mode.
 */
data class DspAudioMetrics(
    val processedSamples: Int,
    val peakNormalized: Float,
    val clippedSamples: Int
)

/**
 * Implementación simulada en Kotlin puro del contrato Rust para pruebas unitarias e interfaz sin binario nativo cargado.
 */
class DummyRustCoreBridge : RustCoreBridgeContract {

    override val isNativeBacked: Boolean = false

    /**
     * Informa la versión del core que esta simulación replica.
     */
    override fun coreVersion(): String {
        // [DUMMY-PRUEBAS]: Versión fija que replica core-rust 0.2.0 sin consultar upiicsa_core_version.
        return "0.2.0"
    }

    /**
     * Selecciona el transporte óptimo replicando la lógica exacta de core-rust/src/transport.rs.
     */
    override fun selectBestTransport(
        usbAvailable: Boolean,
        wifiAvailable: Boolean,
        bluetoothAvailable: Boolean
    ): TransportType? {
        // [DUMMY-PRUEBAS]: Replica en Kotlin puro la selección de core-rust hasta enlazar la biblioteca compartida JNI/JNA.
        return when {
            usbAvailable -> TransportType.USB_CABLE
            wifiAvailable -> TransportType.WIFI_LAN
            bluetoothAvailable -> TransportType.BLUETOOTH
            else -> null
        }
    }

    /**
     * Procesa un bloque de muestras PCM simulando la normalización de Music Mode ejecutada por signal_dsp.rs.
     */
    override fun processMusicModePcm(samples: ShortArray, gainMultiplier: Float): DspAudioMetrics {
        // [DUMMY-PRUEBAS]: Simulación determinista del procesador DSP nativo para pruebas de UI y flujo sin FFI activo.
        var peak = 0f
        var clipped = 0
        val maxLimit = Short.MAX_VALUE * 0.98f

        for (index in samples.indices) {
            val scaled = samples[index] * gainMultiplier
            val absScaled = abs(scaled)
            if (absScaled > peak) {
                peak = absScaled
            }
            val clamped = when {
                scaled > maxLimit -> {
                    clipped++
                    maxLimit
                }
                scaled < -maxLimit -> {
                    clipped++
                    -maxLimit
                }
                else -> scaled
            }
            samples[index] = clamped.roundToInt().toShort()
        }

        return DspAudioMetrics(
            processedSamples = samples.size,
            peakNormalized = peak / Short.MAX_VALUE,
            clippedSamples = clipped
        )
    }

    /**
     * Calcula una suma de verificación determinista equivalente para pruebas sin carga de librería nativa.
     */
    override fun computeFrameChecksum(payload: ByteArray, channelId: Int, sequenceId: Long): Long {
        // [DUMMY-PRUEBAS]: CRC32 IEEE replicado en Kotlin hasta enlazar upiicsa_compute_frame_crc32 vía JNI/JNA.
        return crc32WithHeader(payload, channelId, sequenceId)
    }

    /**
     * Serializa la trama replicando `TransportMultiplexer::encode_wire_bytes`.
     */
    override fun encodeFrame(
        medium: TransportType,
        channel: ServiceChannel,
        sequenceId: Long,
        payload: ByteArray
    ): ByteArray {
        // [DUMMY-PRUEBAS]: Serialización Kotlin equivalente a upiicsa_encode_frame mientras no se cargue el binario nativo.
        val crc = crc32WithHeader(payload, channel.wireId, sequenceId)
        val header = ByteArray(FRAME_HEADER_LEN)
        header[0] = 'U'.code.toByte()
        header[1] = 'C'.code.toByte()
        header[2] = medium.wireId.toByte()
        header[3] = channel.wireId.toByte()
        writeU32(header, 4, sequenceId)
        writeU32(header, 8, payload.size.toLong())
        writeU32(header, 12, crc)
        return header + payload
    }

    /**
     * Valida la trama replicando `TransportMultiplexer::decode_wire_bytes`.
     */
    override fun validateWireFrame(bytes: ByteArray): FrameValidation {
        // [DUMMY-PRUEBAS]: Validación Kotlin equivalente a upiicsa_validate_wire_frame mientras no se cargue el binario nativo.
        if (bytes.size < FRAME_HEADER_LEN) {
            return FrameValidation.Invalid(WireFrameError.INCOMPLETE_HEADER)
        }
        if (bytes[0] != 'U'.code.toByte() || bytes[1] != 'C'.code.toByte()) {
            return FrameValidation.Invalid(WireFrameError.INVALID_SIGNATURE)
        }
        if (TransportType.fromWireId(bytes[2].toInt() and 0xFF) == null) {
            return FrameValidation.Invalid(WireFrameError.UNKNOWN_MEDIUM)
        }
        val channel = ServiceChannel.fromWireId(bytes[3].toInt() and 0xFF)
            ?: return FrameValidation.Invalid(WireFrameError.UNKNOWN_CHANNEL)
        val sequenceId = readU32(bytes, 4)
        val payloadLen = readU32(bytes, 8)
        if (payloadLen > MAX_PAYLOAD_BYTES) {
            return FrameValidation.Invalid(WireFrameError.PAYLOAD_TOO_LARGE)
        }
        if (bytes.size < FRAME_HEADER_LEN + payloadLen) {
            return FrameValidation.Invalid(WireFrameError.INCONSISTENT_LENGTH)
        }
        val payload = bytes.copyOfRange(FRAME_HEADER_LEN, FRAME_HEADER_LEN + payloadLen.toInt())
        val expected = readU32(bytes, 12)
        return if (crc32WithHeader(payload, channel.wireId, sequenceId) == expected) {
            FrameValidation.Valid(channel)
        } else {
            FrameValidation.Invalid(WireFrameError.CHECKSUM_MISMATCH)
        }
    }

    /**
     * Deriva el PIN replicando `pairing::derive_pairing_pin` de core-rust.
     */
    override fun derivePairingPin(desktopId: String, mobileId: String, nonce: Long): Int {
        // [DUMMY-PRUEBAS]: PIN no criptográfico (CRC32 de identificadores ordenados) idéntico al dummy de core-rust/src/pairing.rs.
        val (first, second) = if (desktopId <= mobileId) desktopId to mobileId else mobileId to desktopId
        val material = first.encodeToByteArray() + byteArrayOf(0x1F) + second.encodeToByteArray()
        return (crc32WithHeader(material, PAIRING_TAG, nonce) % PIN_SPACE).toInt()
    }

    private companion object {
        const val FRAME_HEADER_LEN = 16
        const val MAX_PAYLOAD_BYTES = 4L * 1024 * 1024
        const val PAIRING_TAG = 0x50
        const val PIN_SPACE = 1_000_000L

        /**
         * CRC32 IEEE 802.3 sobre (canal, secuencia big-endian, carga útil) idéntico a `compute_crc32` de Rust.
         */
        fun crc32WithHeader(payload: ByteArray, channelId: Int, sequenceId: Long): Long {
            var crc = 0xFFFFFFFFL
            val header = byteArrayOf(
                channelId.toByte(),
                ((sequenceId shr 24) and 0xFF).toByte(),
                ((sequenceId shr 16) and 0xFF).toByte(),
                ((sequenceId shr 8) and 0xFF).toByte(),
                (sequenceId and 0xFF).toByte()
            )
            for (byte in header + payload) {
                crc = crc xor (byte.toLong() and 0xFFL)
                for (bit in 0 until 8) {
                    val mask = if ((crc and 1L) != 0L) 0xEDB88320L else 0L
                    crc = (crc ushr 1) xor mask
                }
            }
            return crc xor 0xFFFFFFFFL
        }

        /**
         * Escribe un u32 big-endian en el arreglo a partir de la posición indicada.
         */
        fun writeU32(target: ByteArray, offset: Int, value: Long) {
            target[offset] = (value shr 24 and 0xFF).toByte()
            target[offset + 1] = (value shr 16 and 0xFF).toByte()
            target[offset + 2] = (value shr 8 and 0xFF).toByte()
            target[offset + 3] = (value and 0xFF).toByte()
        }

        /**
         * Lee un u32 big-endian del arreglo a partir de la posición indicada.
         */
        fun readU32(source: ByteArray, offset: Int): Long {
            var value = 0L
            for (index in 0 until 4) {
                value = (value shl 8) or (source[offset + index].toLong() and 0xFF)
            }
            return value
        }
    }
}
