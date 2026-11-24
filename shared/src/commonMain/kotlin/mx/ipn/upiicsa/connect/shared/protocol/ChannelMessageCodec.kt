package mx.ipn.upiicsa.connect.shared.protocol

import mx.ipn.upiicsa.connect.shared.model.ConnectionDirection
import mx.ipn.upiicsa.connect.shared.model.NotificationResolutionAction

/**
 * Origen de un contenido sincronizado, equivalente a `ContentOrigin` de core-rust/src/protocol.rs.
 */
enum class ContentOrigin(val wireId: Int) {
    DESKTOP(1),
    MOBILE(2)
}

/**
 * Mensajes de aplicación tipados que viajan dentro de la carga útil de una trama, equivalentes a `ChannelMessage` en Rust.
 */
sealed class ChannelMessage {
    /**
     * Respuesta rápida en línea a una notificación.
     */
    data class NotificationReply(val notificationId: String, val replyText: String) : ChannelMessage()
    /**
     * Orden de marcar una notificación como leída.
     */
    data class NotificationMarkRead(val notificationId: String) : ChannelMessage()
    /**
     * Orden de descartar una notificación.
     */
    data class NotificationDismiss(val notificationId: String) : ChannelMessage()

    /**
     * Notificación publicada en el teléfono que se espeja hacia el ordenador.
     */
    data class NotificationPosted(
        val notificationId: String,
        val appName: String,
        val packageName: String,
        val senderTitle: String,
        val messageBody: String,
        val supportsInlineReply: Boolean
    ) : ChannelMessage()
    /**
     * Texto del portapapeles compartido con su origen.
     */
    data class ClipboardText(val origin: ContentOrigin, val text: String) : ChannelMessage()
    /**
     * Orden de activar o detener cámara y micrófono remotos.
     */
    data class CameraMicCommand(
        val cameraEnabled: Boolean,
        val microphoneEnabled: Boolean,
        val useBackCamera: Boolean
    ) : ChannelMessage()
    /**
     * Orden de Music Mode con ganancia en milésimas.
     */
    data class MusicModeCommand(val enabled: Boolean, val gainMilli: Int) : ChannelMessage()
    /**
     * Saludo inicial de enlace con dirección, nombre del equipo, PIN y versión del formato de mensajes.
     */
    data class HandshakeHello(
        val direction: ConnectionDirection,
        val deviceName: String,
        val pairingPin: Int,
        val protocolVersion: Int = ChannelMessageCodec.PROTOCOL_VERSION
    ) : ChannelMessage()
    /**
     * Latido periódico con marca de tiempo para medir latencia.
     */
    data class Heartbeat(val timestampMs: Long) : ChannelMessage()

    /**
     * Canal lógico por el que debe enviarse el mensaje.
     */
    val channel: ServiceChannel
        get() = when (this) {
            is NotificationReply, is NotificationMarkRead, is NotificationDismiss, is NotificationPosted -> ServiceChannel.NOTIFICATION_RESOLUTION
            is ClipboardText -> ServiceChannel.CLIPBOARD_SYNC
            is CameraMicCommand -> ServiceChannel.WEBCAM_MIC_STREAM
            is MusicModeCommand -> ServiceChannel.MUSIC_MODE_AUDIO
            is HandshakeHello, is Heartbeat -> ServiceChannel.SESSION_CONTROL
        }

    companion object {
        /**
         * Convierte una acción de resolución de notificación del dominio en su mensaje de protocolo.
         */
        fun fromResolution(action: NotificationResolutionAction): ChannelMessage = when (action) {
            is NotificationResolutionAction.QuickReply -> NotificationReply(action.notificationId, action.replyText)
            is NotificationResolutionAction.MarkAsRead -> NotificationMarkRead(action.notificationId)
            is NotificationResolutionAction.Dismiss -> NotificationDismiss(action.notificationId)
        }
    }
}

/**
 * Tipos de falla al decodificar un mensaje, equivalentes a `ProtocolError` en Rust.
 */
enum class ProtocolErrorKind {
    EMPTY_PAYLOAD,
    UNKNOWN_TAG,
    TRUNCATED,
    TEXT_TOO_LONG,
    INVALID_UTF8,
    INVALID_ENUM_VALUE,
    TRAILING_BYTES
}

/**
 * Excepción lanzada por el códec cuando una carga útil no respeta el formato binario del protocolo.
 */
class ChannelProtocolException(val kind: ProtocolErrorKind) : Exception("Mensaje de protocolo inválido: $kind")

/**
 * Códec binario en Kotlin puro compatible byte a byte con core-rust/src/protocol.rs (textos con prefijo u16 big-endian).
 */
object ChannelMessageCodec {

    /**
     * Versión del formato de mensajes (igual a `PROTOCOL_VERSION` de protocol.rs); ambos extremos deben coincidir.
     */
    const val PROTOCOL_VERSION = 1


    private const val TAG_NOTIFICATION_REPLY = 0x10
    private const val TAG_NOTIFICATION_MARK_READ = 0x11
    private const val TAG_NOTIFICATION_DISMISS = 0x12
    private const val TAG_NOTIFICATION_POSTED = 0x13
    private const val TAG_CLIPBOARD_TEXT = 0x20
    private const val TAG_CAMERA_MIC_COMMAND = 0x30
    private const val TAG_MUSIC_MODE_COMMAND = 0x40
    private const val TAG_HANDSHAKE_HELLO = 0x50
    private const val TAG_HEARTBEAT = 0x60
    private const val MAX_TEXT_BYTES = 0xFFFF

    /**
     * Codifica un mensaje en su representación binaria de protocolo.
     */
    fun encode(message: ChannelMessage): ByteArray {
        val out = ByteWriter()
        when (message) {
            is ChannelMessage.NotificationReply -> {
                out.byte(TAG_NOTIFICATION_REPLY)
                out.text(message.notificationId)
                out.text(message.replyText)
            }
            is ChannelMessage.NotificationMarkRead -> {
                out.byte(TAG_NOTIFICATION_MARK_READ)
                out.text(message.notificationId)
            }
            is ChannelMessage.NotificationDismiss -> {
                out.byte(TAG_NOTIFICATION_DISMISS)
                out.text(message.notificationId)
            }
            is ChannelMessage.NotificationPosted -> {
                out.byte(TAG_NOTIFICATION_POSTED)
                out.text(message.notificationId)
                out.text(message.appName)
                out.text(message.packageName)
                out.text(message.senderTitle)
                out.text(message.messageBody)
                out.flag(message.supportsInlineReply)
            }
            is ChannelMessage.ClipboardText -> {
                out.byte(TAG_CLIPBOARD_TEXT)
                out.byte(message.origin.wireId)
                out.text(message.text)
            }
            is ChannelMessage.CameraMicCommand -> {
                out.byte(TAG_CAMERA_MIC_COMMAND)
                out.flag(message.cameraEnabled)
                out.flag(message.microphoneEnabled)
                out.flag(message.useBackCamera)
            }
            is ChannelMessage.MusicModeCommand -> {
                out.byte(TAG_MUSIC_MODE_COMMAND)
                out.flag(message.enabled)
                out.u16(message.gainMilli.coerceIn(0, 0xFFFF))
            }
            is ChannelMessage.HandshakeHello -> {
                out.byte(TAG_HANDSHAKE_HELLO)
                out.byte(if (message.direction == ConnectionDirection.DESKTOP_TO_MOBILE) 1 else 2)
                out.text(message.deviceName)
                out.u32(message.pairingPin.toLong())
                out.u16(message.protocolVersion.coerceIn(0, 0xFFFF))
            }
            is ChannelMessage.Heartbeat -> {
                out.byte(TAG_HEARTBEAT)
                out.u64(message.timestampMs)
            }
        }
        return out.toByteArray()
    }

    /**
     * Decodifica una carga útil binaria; lanza [ChannelProtocolException] si el formato es inválido.
     */
    fun decode(bytes: ByteArray): ChannelMessage {
        if (bytes.isEmpty()) {
            throw ChannelProtocolException(ProtocolErrorKind.EMPTY_PAYLOAD)
        }
        val reader = ByteReader(bytes, start = 1)
        val message = when (bytes[0].toInt() and 0xFF) {
            TAG_NOTIFICATION_REPLY -> ChannelMessage.NotificationReply(reader.text(), reader.text())
            TAG_NOTIFICATION_MARK_READ -> ChannelMessage.NotificationMarkRead(reader.text())
            TAG_NOTIFICATION_DISMISS -> ChannelMessage.NotificationDismiss(reader.text())
            TAG_NOTIFICATION_POSTED -> ChannelMessage.NotificationPosted(
                reader.text(), reader.text(), reader.text(), reader.text(), reader.text(), reader.flag()
            )
            TAG_CLIPBOARD_TEXT -> {
                val origin = when (reader.u8()) {
                    1 -> ContentOrigin.DESKTOP
                    2 -> ContentOrigin.MOBILE
                    else -> throw ChannelProtocolException(ProtocolErrorKind.INVALID_ENUM_VALUE)
                }
                ChannelMessage.ClipboardText(origin, reader.text())
            }
            TAG_CAMERA_MIC_COMMAND -> ChannelMessage.CameraMicCommand(reader.flag(), reader.flag(), reader.flag())
            TAG_MUSIC_MODE_COMMAND -> ChannelMessage.MusicModeCommand(reader.flag(), reader.u16())
            TAG_HANDSHAKE_HELLO -> {
                val direction = when (reader.u8()) {
                    1 -> ConnectionDirection.DESKTOP_TO_MOBILE
                    2 -> ConnectionDirection.MOBILE_TO_DESKTOP
                    else -> throw ChannelProtocolException(ProtocolErrorKind.INVALID_ENUM_VALUE)
                }
                ChannelMessage.HandshakeHello(direction, reader.text(), reader.u32().toInt(), reader.u16())
            }
            TAG_HEARTBEAT -> ChannelMessage.Heartbeat(reader.u64())
            else -> throw ChannelProtocolException(ProtocolErrorKind.UNKNOWN_TAG)
        }
        reader.finish()
        return message
    }

    /**
     * Variante tolerante de [decode] que devuelve `null` ante cualquier carga útil inválida.
     */
    fun decodeOrNull(bytes: ByteArray): ChannelMessage? = try {
        decode(bytes)
    } catch (error: ChannelProtocolException) {
        null
    }

    /**
     * Acumulador de bytes en orden big-endian.
     */
    private class ByteWriter {
        private val buffer = ArrayList<Byte>(32)

        /**
         * Agrega un byte sin signo.
         */
        fun byte(value: Int) {
            buffer.add(value.toByte())
        }

        /**
         * Agrega un booleano como 0 o 1.
         */
        fun flag(value: Boolean) = byte(if (value) 1 else 0)

        /**
         * Agrega un entero u16 big-endian.
         */
        fun u16(value: Int) {
            byte(value shr 8 and 0xFF)
            byte(value and 0xFF)
        }

        /**
         * Agrega un entero u32 big-endian.
         */
        fun u32(value: Long) {
            for (shift in intArrayOf(24, 16, 8, 0)) {
                byte((value shr shift and 0xFF).toInt())
            }
        }

        /**
         * Agrega un entero u64 big-endian.
         */
        fun u64(value: Long) {
            for (shift in 56 downTo 0 step 8) {
                byte((value ushr shift and 0xFF).toInt())
            }
        }

        /**
         * Agrega un texto UTF-8 precedido por su longitud u16.
         */
        fun text(value: String) {
            val raw = value.encodeToByteArray()
            if (raw.size > MAX_TEXT_BYTES) {
                throw ChannelProtocolException(ProtocolErrorKind.TEXT_TOO_LONG)
            }
            u16(raw.size)
            raw.forEach { buffer.add(it) }
        }

        /**
         * Devuelve el contenido acumulado.
         */
        fun toByteArray(): ByteArray = buffer.toByteArray()
    }

    /**
     * Lector secuencial con verificación de límites.
     */
    private class ByteReader(private val bytes: ByteArray, start: Int) {
        private var position = start

        /**
         * Lee un byte sin signo o reporta truncamiento.
         */
        fun u8(): Int {
            if (position >= bytes.size) {
                throw ChannelProtocolException(ProtocolErrorKind.TRUNCATED)
            }
            return bytes[position++].toInt() and 0xFF
        }

        /**
         * Lee un booleano estricto (0 o 1).
         */
        fun flag(): Boolean = when (u8()) {
            0 -> false
            1 -> true
            else -> throw ChannelProtocolException(ProtocolErrorKind.INVALID_ENUM_VALUE)
        }

        /**
         * Lee un entero u16 big-endian.
         */
        fun u16(): Int = (u8() shl 8) or u8()

        /**
         * Lee un entero u32 big-endian como Long sin signo.
         */
        fun u32(): Long = (u16().toLong() shl 16) or u16().toLong()

        /**
         * Lee un entero u64 big-endian.
         */
        fun u64(): Long = (u32() shl 32) or u32()

        /**
         * Lee un texto UTF-8 prefijado por longitud u16 validando su codificación.
         */
        fun text(): String {
            val length = u16()
            if (position + length > bytes.size) {
                throw ChannelProtocolException(ProtocolErrorKind.TRUNCATED)
            }
            val raw = bytes.copyOfRange(position, position + length)
            position += length
            return try {
                raw.decodeToString(throwOnInvalidSequence = true)
            } catch (error: CharacterCodingException) {
                throw ChannelProtocolException(ProtocolErrorKind.INVALID_UTF8)
            }
        }

        /**
         * Verifica que no queden bytes sin consumir.
         */
        fun finish() {
            if (position != bytes.size) {
                throw ChannelProtocolException(ProtocolErrorKind.TRAILING_BYTES)
            }
        }
    }
}
