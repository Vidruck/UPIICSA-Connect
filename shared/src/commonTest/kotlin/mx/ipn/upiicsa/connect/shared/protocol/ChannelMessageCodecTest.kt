package mx.ipn.upiicsa.connect.shared.protocol

import mx.ipn.upiicsa.connect.shared.model.ConnectionDirection
import mx.ipn.upiicsa.connect.shared.model.NotificationResolutionAction
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Pruebas del códec Kotlin y su compatibilidad byte a byte con core-rust/src/protocol.rs.
 */
class ChannelMessageCodecTest {

    /**
     * Verifica la ida y vuelta de todos los tipos de mensaje.
     */
    @Test
    fun idaYVueltaDeTodosLosMensajes() {
        val mensajes = listOf(
            ChannelMessage.NotificationReply("notif-1", "Voy en camino | llego 14:30"),
            ChannelMessage.NotificationMarkRead("notif-2"),
            ChannelMessage.NotificationDismiss("notif-3"),
            ChannelMessage.NotificationPosted("notif-4", "WhatsApp", "com.whatsapp", "Equipo UPIICSA", "¿Llegas a la reunión?", true),
            ChannelMessage.ClipboardText(ContentOrigin.MOBILE, "Código: ñandú ✓"),
            ChannelMessage.CameraMicCommand(cameraEnabled = true, microphoneEnabled = false, useBackCamera = true),
            ChannelMessage.MusicModeCommand(enabled = true, gainMilli = 1150),
            ChannelMessage.HandshakeHello(ConnectionDirection.MOBILE_TO_DESKTOP, "Pixel 8 Pro", 482_913),
            ChannelMessage.Heartbeat(1_700_000_000_123L)
        )
        for (mensaje in mensajes) {
            assertEquals(mensaje, ChannelMessageCodec.decode(ChannelMessageCodec.encode(mensaje)))
        }
    }

    /**
     * Verifica los mismos vectores binarios fijados en la prueba Rust `prueba_vector_binario_compartido_con_kotlin`.
     */
    @Test
    fun vectoresBinariosCompartidosConRust() {
        val respuesta = ChannelMessageCodec.encode(ChannelMessage.NotificationReply("n1", "Ok"))
        assertContentEquals(byteArrayOf(0x10, 0, 2, 'n'.code.toByte(), '1'.code.toByte(), 0, 2, 'O'.code.toByte(), 'k'.code.toByte()), respuesta)
        val musica = ChannelMessageCodec.encode(ChannelMessage.MusicModeCommand(true, 1150))
        assertContentEquals(byteArrayOf(0x40, 1, 0x04, 0x7E), musica)
        val aviso = ChannelMessageCodec.encode(ChannelMessage.NotificationPosted("n1", "Wa", "w", "Al", "Hi", true))
        val esperado = listOf(0x13, 0, 2, 'n', '1', 0, 2, 'W', 'a', 0, 1, 'w', 0, 2, 'A', 'l', 0, 2, 'H', 'i', 1)
            .map { if (it is Char) it.code.toByte() else (it as Int).toByte() }
            .toByteArray()
        assertContentEquals(esperado, aviso)
    }

    /**
     * Verifica el rechazo de cargas útiles malformadas con el tipo de error esperado.
     */
    @Test
    fun rechazoDeMensajesMalformados() {
        val casos = mapOf(
            byteArrayOf() to ProtocolErrorKind.EMPTY_PAYLOAD,
            byteArrayOf(0x99.toByte()) to ProtocolErrorKind.UNKNOWN_TAG,
            byteArrayOf(0x11, 0, 5, 'a'.code.toByte()) to ProtocolErrorKind.TRUNCATED,
            byteArrayOf(0x11, 0, 2, 0xFF.toByte(), 0xFE.toByte()) to ProtocolErrorKind.INVALID_UTF8,
            byteArrayOf(0x30, 1, 2, 0) to ProtocolErrorKind.INVALID_ENUM_VALUE,
            byteArrayOf(0x11, 0, 1, 'a'.code.toByte(), 0) to ProtocolErrorKind.TRAILING_BYTES
        )
        for ((bytes, esperado) in casos) {
            val error = assertFailsWith<ChannelProtocolException> { ChannelMessageCodec.decode(bytes) }
            assertEquals(esperado, error.kind)
        }
        assertNull(ChannelMessageCodec.decodeOrNull(byteArrayOf(0x60, 1)))
    }

    /**
     * Verifica la conversión de acciones de dominio en mensajes y su canal lógico.
     */
    @Test
    fun conversionDeAccionesDeResolucion() {
        val mensaje = ChannelMessage.fromResolution(NotificationResolutionAction.Dismiss("x"))
        assertEquals(ChannelMessage.NotificationDismiss("x"), mensaje)
        assertEquals(ServiceChannel.NOTIFICATION_RESOLUTION, mensaje.channel)
        assertEquals(ServiceChannel.SESSION_CONTROL, ChannelMessage.Heartbeat(1).channel)
    }
}
