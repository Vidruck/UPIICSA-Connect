package mx.ipn.upiicsa.connect.shared.protocol

import mx.ipn.upiicsa.connect.shared.model.TransportType

/**
 * Trama completa extraída de un flujo de bytes, con su carga útil ya verificada por CRC32.
 */
class ReassembledFrame(
    val medium: TransportType,
    val channel: ServiceChannel,
    val sequenceId: Long,
    val payload: ByteArray
)

/**
 * Resultado de intentar extraer una trama del búfer: trama válida o error de la trama descartada.
 */
sealed class ReassemblyResult {

    /**
     * Trama íntegra lista para decodificar su mensaje.
     */
    class Frame(val frame: ReassembledFrame) : ReassemblyResult()

    /**
     * Trama rechazada y descartada del búfer por el motivo indicado.
     */
    data class Rejected(val error: WireFrameError) : ReassemblyResult()
}

/**
 * Estadísticas acumuladas del reensamblador, equivalentes a `StreamDecoderStats` en Rust.
 */
data class ReassemblerStats(
    val framesOk: Long = 0,
    val framesRejected: Long = 0,
    val bytesDiscarded: Long = 0
)

/**
 * Reensambla tramas `UpiicsaFrame` desde lecturas parciales de un socket y se resincroniza ante basura.
 * Replica la lógica de `core-rust/src/stream_decoder.rs` y delega la verificación CRC32 al core vía [RustCoreBridgeContract].
 */
class FrameReassembler(private val rustCore: RustCoreBridgeContract) {

    private var buffer = ByteArray(INITIAL_CAPACITY)
    private var size = 0

    /**
     * Estadísticas acumuladas desde la creación del reensamblador.
     */
    var stats: ReassemblerStats = ReassemblerStats()
        private set

    /**
     * Cantidad de bytes pendientes que aún no forman una trama completa.
     */
    val pendingBytes: Int
        get() = size

    /**
     * Agrega al búfer interno los primeros `length` bytes recién leídos del transporte.
     */
    fun feed(chunk: ByteArray, length: Int = chunk.size) {
        require(length in 0..chunk.size) { "Longitud fuera de rango: $length" }
        ensureCapacity(size + length)
        chunk.copyInto(buffer, destinationOffset = size, startIndex = 0, endIndex = length)
        size += length
    }

    /**
     * Extrae la siguiente trama: `null` si faltan bytes, [ReassemblyResult.Rejected] si se descartó una trama inválida.
     */
    fun nextFrame(): ReassemblyResult? {
        discardUntilSignature()
        if (size < FRAME_HEADER_LEN) {
            return null
        }
        val headerError = headerError()
        if (headerError != null) {
            // [DECISIÓN TÉCNICA]: Igual que en Rust, ante cabecera inválida solo se descarta la firma para buscar la siguiente coincidencia "UC".
            discard(SIGNATURE.size)
            stats = stats.copy(framesRejected = stats.framesRejected + 1)
            return ReassemblyResult.Rejected(headerError)
        }
        val totalLength = FRAME_HEADER_LEN + readU32(8).toInt()
        if (size < totalLength) {
            return null
        }
        val frameBytes = buffer.copyOfRange(0, totalLength)
        removePrefix(totalLength)
        return when (val validation = rustCore.validateWireFrame(frameBytes)) {
            is FrameValidation.Valid -> {
                stats = stats.copy(framesOk = stats.framesOk + 1)
                ReassemblyResult.Frame(
                    ReassembledFrame(
                        medium = TransportType.fromWireId(frameBytes[2].toInt() and 0xFF) ?: TransportType.USB_CABLE,
                        channel = validation.channel,
                        sequenceId = readU32(frameBytes, 4),
                        payload = frameBytes.copyOfRange(FRAME_HEADER_LEN, totalLength)
                    )
                )
            }
            is FrameValidation.Invalid -> {
                stats = stats.copy(
                    framesRejected = stats.framesRejected + 1,
                    bytesDiscarded = stats.bytesDiscarded + totalLength
                )
                ReassemblyResult.Rejected(validation.error)
            }
        }
    }

    /**
     * Extrae todas las tramas válidas disponibles ignorando las rechazadas (ya contabilizadas en [stats]).
     */
    fun drainValidFrames(): List<ReassembledFrame> {
        val frames = mutableListOf<ReassembledFrame>()
        while (true) {
            when (val result = nextFrame() ?: break) {
                is ReassemblyResult.Frame -> frames += result.frame
                is ReassemblyResult.Rejected -> Unit
            }
        }
        return frames
    }

    /**
     * Valida firma, medio, canal y tamaño máximo de la cabecera presente al inicio del búfer.
     */
    private fun headerError(): WireFrameError? = when {
        TransportType.fromWireId(buffer[2].toInt() and 0xFF) == null -> WireFrameError.UNKNOWN_MEDIUM
        ServiceChannel.fromWireId(buffer[3].toInt() and 0xFF) == null -> WireFrameError.UNKNOWN_CHANNEL
        readU32(8) > MAX_PAYLOAD_BYTES -> WireFrameError.PAYLOAD_TOO_LARGE
        else -> null
    }

    /**
     * Elimina bytes iniciales hasta la firma "UC", conservando una posible 'U' final incompleta.
     */
    private fun discardUntilSignature() {
        var index = 0
        while (index + 1 < size) {
            if (buffer[index] == SIGNATURE[0] && buffer[index + 1] == SIGNATURE[1]) {
                discard(index)
                return
            }
            index++
        }
        val keep = if (size > 0 && buffer[size - 1] == SIGNATURE[0]) 1 else 0
        discard(size - keep)
    }

    /**
     * Descarta `count` bytes del inicio del búfer registrándolos en las estadísticas.
     */
    private fun discard(count: Int) {
        val dropped = count.coerceAtMost(size)
        if (dropped == 0) {
            return
        }
        removePrefix(dropped)
        stats = stats.copy(bytesDiscarded = stats.bytesDiscarded + dropped)
    }

    /**
     * Desplaza el contenido del búfer eliminando los primeros `count` bytes.
     */
    private fun removePrefix(count: Int) {
        buffer.copyInto(buffer, destinationOffset = 0, startIndex = count, endIndex = size)
        size -= count
    }

    /**
     * Garantiza capacidad para `required` bytes duplicando el búfer cuando es necesario.
     */
    private fun ensureCapacity(required: Int) {
        if (required > buffer.size) {
            buffer = buffer.copyOf(maxOf(required, buffer.size * 2))
        }
    }

    /**
     * Lee un `u32` big-endian del búfer interno en la posición indicada.
     */
    private fun readU32(offset: Int): Long = readU32(buffer, offset)

    private companion object {
        const val FRAME_HEADER_LEN = 16
        const val MAX_PAYLOAD_BYTES = 4L * 1024 * 1024
        const val INITIAL_CAPACITY = 8 * 1024
        val SIGNATURE = byteArrayOf('U'.code.toByte(), 'C'.code.toByte())

        /**
         * Lee un `u32` big-endian de un arreglo arbitrario.
         */
        fun readU32(bytes: ByteArray, offset: Int): Long =
            ((bytes[offset].toLong() and 0xFF) shl 24) or
                ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
                ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
                (bytes[offset + 3].toLong() and 0xFF)
    }
}
