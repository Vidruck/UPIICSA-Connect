package mx.ipn.upiicsa.connect.desktop.adapters.transport.tcp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mx.ipn.upiicsa.connect.shared.link.FrameSocketChannel
import mx.ipn.upiicsa.connect.shared.link.UpiicsaLinkDefaults
import mx.ipn.upiicsa.connect.shared.model.TransportType
import mx.ipn.upiicsa.connect.shared.protocol.RustCoreBridgeContract
import java.io.Closeable
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException

/**
 * Servidor TCP del ordenador que acepta un único teléfono a la vez y comparte el canal activo
 * entre el adaptador de transporte y el adaptador de eventos del teléfono.
 */
class TcpLinkSession(
    private val rustCore: RustCoreBridgeContract,
    private val requestedPort: Int = UpiicsaLinkDefaults.TCP_PORT,
    private val bindAddress: InetAddress = InetAddress.getLoopbackAddress()
) : Closeable {

    private var serverSocket: ServerSocket? = null

    @Volatile
    private var channel: FrameSocketChannel? = null

    /**
     * Abre el socket de escucha si aún no existe y retorna el puerto efectivo (útil cuando se solicita el puerto 0).
     */
    @Synchronized
    fun listen(): Int {
        val server = serverSocket ?: ServerSocket().apply {
            reuseAddress = true
            // [DECISIÓN TÉCNICA]: Solo se escucha en loopback: el cable USB llega por `adb reverse` y no se expone un puerto sin mTLS en la LAN.
            bind(InetSocketAddress(bindAddress, requestedPort), 1)
        }.also { serverSocket = it }
        return server.localPort
    }

    /**
     * Espera a que el teléfono se conecte (máximo `timeoutMs`) reemplazando cualquier canal anterior; `null` si no llegó.
     */
    suspend fun awaitPhone(medium: TransportType, timeoutMs: Int): FrameSocketChannel? = withContext(Dispatchers.IO) {
        closeChannel()
        listen()
        val server = checkNotNull(serverSocket)
        server.soTimeout = timeoutMs
        try {
            val socket = server.accept()
            socket.tcpNoDelay = true
            FrameSocketChannel(socket, rustCore, medium).also { channel = it }
        } catch (error: SocketTimeoutException) {
            null
        }
    }

    /**
     * Canal activo si sigue abierto.
     */
    fun activeChannel(): FrameSocketChannel? = channel?.takeIf { it.isOpen }

    /**
     * Cierra el canal con el teléfono manteniendo el servidor a la escucha.
     */
    fun closeChannel() {
        channel?.close()
        channel = null
    }

    /**
     * Cierra el canal y el servidor.
     */
    @Synchronized
    override fun close() {
        closeChannel()
        serverSocket?.close()
        serverSocket = null
    }
}
