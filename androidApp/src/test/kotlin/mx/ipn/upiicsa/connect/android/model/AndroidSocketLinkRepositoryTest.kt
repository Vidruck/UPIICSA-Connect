package mx.ipn.upiicsa.connect.android.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import mx.ipn.upiicsa.connect.shared.link.FrameSocketChannel
import mx.ipn.upiicsa.connect.shared.model.ConnectionDirection
import mx.ipn.upiicsa.connect.shared.model.NotificationResolutionAction
import mx.ipn.upiicsa.connect.shared.model.PhoneNotificationItem
import mx.ipn.upiicsa.connect.shared.model.TransportType
import mx.ipn.upiicsa.connect.shared.protocol.ChannelMessage
import mx.ipn.upiicsa.connect.shared.protocol.ContentOrigin
import mx.ipn.upiicsa.connect.shared.protocol.DummyRustCoreBridge
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Pruebas JVM (sin emulador) del repositorio de enlace real contra un servidor TCP local que actúa como el ordenador.
 */
class AndroidSocketLinkRepositoryTest {

    private val core = DummyRustCoreBridge()

    /**
     * Verifica saludo, eco de latidos, PIN del ordenador, portapapeles, resoluciones y espejo de notificaciones.
     */
    @Test
    fun intercambioCompletoConElOrdenador(): Unit = runBlocking {
        ServerSocket().use { server ->
            server.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
            val repository = AndroidSocketLinkRepository(core, host = "127.0.0.1", port = server.localPort)
            withTimeout(10_000) {
                val accepted = async(Dispatchers.IO) { server.accept() }
                assertNull(repository.connect(ConnectionDirection.MOBILE_TO_DESKTOP, deviceName = "Pixel de prueba"))
                val desktop = FrameSocketChannel(accepted.await(), core, TransportType.USB_CABLE)
                val fromPhone = async { desktop.incoming().take(4).toList() }
                val events = async { repository.events().take(5).toList() }

                repository.publishClipboard("desde el teléfono")
                repository.publishNotification(
                    PhoneNotificationItem("n-3", "Telegram", "org.telegram", "Ana", "Hola", "10:00", supportsInlineReply = false)
                )
                desktop.send(ChannelMessage.HandshakeHello(ConnectionDirection.MOBILE_TO_DESKTOP, "PC UPIICSA", 482_913))
                desktop.send(ChannelMessage.Heartbeat(1_234L))
                desktop.send(ChannelMessage.ClipboardText(ContentOrigin.DESKTOP, "desde la PC"))
                desktop.send(ChannelMessage.NotificationReply("n-1", "Voy"))
                desktop.send(ChannelMessage.NotificationDismiss("n-2"))

                assertEquals(
                    listOf(
                        DesktopLinkEvent.Hello("PC UPIICSA", 482_913),
                        DesktopLinkEvent.Heartbeat,
                        DesktopLinkEvent.ClipboardFromDesktop("desde la PC"),
                        DesktopLinkEvent.Resolution(NotificationResolutionAction.QuickReply("n-1", "Voy")),
                        DesktopLinkEvent.Resolution(NotificationResolutionAction.Dismiss("n-2"))
                    ),
                    events.await()
                )
                val received = fromPhone.await()
                assertEquals(ChannelMessage.HandshakeHello(ConnectionDirection.MOBILE_TO_DESKTOP, "Pixel de prueba", 0), received[0])
                assertEquals(
                    setOf(
                        ChannelMessage.Heartbeat(1_234L),
                        ChannelMessage.ClipboardText(ContentOrigin.MOBILE, "desde el teléfono"),
                        ChannelMessage.NotificationPosted("n-3", "Telegram", "org.telegram", "Ana", "Hola", false)
                    ),
                    received.drop(1).toSet()
                )
                desktop.close()
                repository.disconnect()
            }
        }
    }

    /**
     * Verifica que sin ordenador escuchando la conexión devuelva un mensaje de error en lugar de lanzar excepción.
     */
    @Test
    fun sinOrdenadorDevuelveError(): Unit = runBlocking {
        val freePort = withContext(Dispatchers.IO) { ServerSocket(0).use { it.localPort } }
        val repository = AndroidSocketLinkRepository(core, host = "127.0.0.1", port = freePort)
        assertNotNull(repository.connect(ConnectionDirection.MOBILE_TO_DESKTOP, deviceName = "Pixel de prueba"))
    }
}
