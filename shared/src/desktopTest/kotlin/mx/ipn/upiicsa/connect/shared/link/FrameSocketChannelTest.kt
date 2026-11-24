package mx.ipn.upiicsa.connect.shared.link

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mx.ipn.upiicsa.connect.shared.model.TransportType
import mx.ipn.upiicsa.connect.shared.protocol.ChannelMessage
import mx.ipn.upiicsa.connect.shared.protocol.DummyRustCoreBridge
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Pruebas del canal de sockets compartido entre escritorio y Android.
 */
class FrameSocketChannelTest {

    private val core = DummyRustCoreBridge()

    /**
     * Verifica que cancelar la recolección (por ejemplo con `first`) cierre el socket en lugar de bloquear el hilo lector.
     */
    @Test
    fun cancelarLaRecoleccionCierraElSocket(): Unit = runBlocking {
        ServerSocket().use { server ->
            server.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
            withTimeout(5_000) {
                val accepted = async(Dispatchers.IO) { server.accept() }
                val client = FrameSocketChannel.connect("127.0.0.1", server.localPort, core, TransportType.USB_CABLE)
                val peer = FrameSocketChannel(accepted.await(), core, TransportType.USB_CABLE)
                peer.send(ChannelMessage.Heartbeat(7L))
                assertEquals(ChannelMessage.Heartbeat(7L), client.incoming().first())
                assertFalse(client.isOpen)
                peer.close()
            }
        }
    }
}
