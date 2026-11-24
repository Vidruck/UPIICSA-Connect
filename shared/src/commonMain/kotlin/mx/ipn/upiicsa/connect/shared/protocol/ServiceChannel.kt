package mx.ipn.upiicsa.connect.shared.protocol

/**
 * Canales lógicos multiplexados en una sesión PC-Android, idénticos a `ServiceChannel` de core-rust/src/transport.rs.
 */
enum class ServiceChannel(val wireId: Int, val displayLabel: String) {
    NOTIFICATION_RESOLUTION(1, "Notificaciones"),
    CLIPBOARD_SYNC(2, "Portapapeles"),
    SECONDARY_DISPLAY_VIDEO(3, "Monitor secundario"),
    WEBCAM_MIC_STREAM(4, "Cámara y micrófono"),
    MUSIC_MODE_AUDIO(5, "Music Mode"),
    SESSION_CONTROL(6, "Control de sesión");

    companion object {
        /**
         * Obtiene el canal correspondiente a un identificador del protocolo binario, o `null` si es desconocido.
         */
        fun fromWireId(wireId: Int): ServiceChannel? = entries.firstOrNull { it.wireId == wireId }
    }
}

/**
 * Códigos de error de validación de tramas devueltos por `upiicsa_validate_wire_frame` (WireFrameError en Rust).
 */
enum class WireFrameError(val code: Int, val description: String) {
    INCOMPLETE_HEADER(-1, "Trama incompleta"),
    INVALID_SIGNATURE(-2, "Firma de protocolo inválida"),
    UNKNOWN_MEDIUM(-3, "Medio de transporte desconocido"),
    UNKNOWN_CHANNEL(-4, "Canal de servicio desconocido"),
    PAYLOAD_TOO_LARGE(-5, "Carga útil excede el máximo"),
    INCONSISTENT_LENGTH(-6, "Longitud inconsistente"),
    CHECKSUM_MISMATCH(-7, "Error de integridad CRC32");

    companion object {
        /**
         * Traduce un código negativo del core nativo en el error tipado equivalente.
         */
        fun fromCode(code: Int): WireFrameError? = entries.firstOrNull { it.code == code }
    }
}

/**
 * Resultado de validar una trama serializada: canal lógico si es válida o error tipado en caso contrario.
 */
sealed class FrameValidation {
    /**
     * Trama íntegra perteneciente al canal indicado.
     */
    data class Valid(val channel: ServiceChannel) : FrameValidation()
    /**
     * Trama rechazada con el error detectado.
     */
    data class Invalid(val error: WireFrameError) : FrameValidation()

    companion object {
        /**
         * Construye el resultado a partir del entero devuelto por la función FFI de validación.
         */
        fun fromNativeCode(code: Int): FrameValidation {
            val channel = ServiceChannel.fromWireId(code)
            return if (channel != null) {
                Valid(channel)
            } else {
                Invalid(WireFrameError.fromCode(code) ?: WireFrameError.INVALID_SIGNATURE)
            }
        }
    }
}
