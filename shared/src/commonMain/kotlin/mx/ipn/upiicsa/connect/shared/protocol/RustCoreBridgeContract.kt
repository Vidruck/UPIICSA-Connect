package mx.ipn.upiicsa.connect.shared.protocol

import mx.ipn.upiicsa.connect.shared.model.TransportType
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Define el contrato de interoperabilidad entre los módulos Kotlin Multiplatform y el núcleo nativo en Rust.
 */
interface RustCoreBridgeContract {

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
        // [DUMMY-PRUEBAS]: Hash determinista de prueba hasta enlazar upiicsa_compute_frame_crc32 vía JNI.
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
}
