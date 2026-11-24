package mx.ipn.upiicsa.connect.shared.link

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import mx.ipn.upiicsa.connect.shared.model.TransportType
import mx.ipn.upiicsa.connect.shared.protocol.ChannelMessage
import mx.ipn.upiicsa.connect.shared.protocol.ChannelMessageCodec
import mx.ipn.upiicsa.connect.shared.protocol.ChannelProtocolException
import mx.ipn.upiicsa.connect.shared.protocol.FrameReassembler
import mx.ipn.upiicsa.connect.shared.protocol.RustCoreBridgeContract
import mx.ipn.upiicsa.connect.shared.protocol.ServiceChannel
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Valores por omisión del enlace real por TCP compartidos entre escritorio y Android.
 */
object UpiicsaLinkDefaults {

    /**
     * Puerto TCP del servidor de escritorio (también usado en `adb reverse tcp:47800 tcp:47800`).
     */
    const val TCP_PORT = 47800

    /**
     * Intervalo entre latidos enviados por el ordenador.
     */
    const val HEARTBEAT_INTERVAL_MS = 1_000L

    /**
     * Tiempo sin latidos tras el cual el enlace se considera caído (igual a `LinkSupervisorConfig` en Rust).
     */
    const val HEARTBEAT_TIMEOUT_MS = 3_000L

    /**
     * Tiempo máximo para establecer la conexión TCP.
     */
    const val CONNECT_TIMEOUT_MS = 5_000
}

/**
 * Canal bidireccional de mensajes [ChannelMessage] sobre un socket TCP: cada mensaje viaja como `UpiicsaFrame`
 * serializada por el core y las lecturas parciales se reensamblan con [FrameReassembler].
 */
class FrameSocketChannel(
    private val socket: Socket,
    private val rustCore: RustCoreBridgeContract,
    private val medium: TransportType
) : Closeable {

    private val output = BufferedOutputStream(socket.getOutputStream())
    private val writeLock = Mutex()
    private var nextSequence = 1L

    /**
     * Mensajes con trama íntegra pero contenido no decodificable (descartados sin cerrar el enlace).
     */
    @Volatile
    var malformedMessages: Long = 0L
        private set

    /**
     * Indica si el socket sigue abierto.
     */
    val isOpen: Boolean
        get() = !socket.isClosed

    /**
     * Serializa el mensaje como trama del core y lo escribe en el socket de forma exclusiva.
     */
    suspend fun send(message: ChannelMessage) = sendRaw(message.channel, ChannelMessageCodec.encode(message))

    /**
     * Envía una carga útil ya codificada por el canal lógico indicado (por ejemplo, cuadros del monitor secundario).
     */
    suspend fun sendRaw(channel: ServiceChannel, payload: ByteArray) {
        withContext(Dispatchers.IO) {
            writeLock.withLock {
                val frame = rustCore.encodeFrame(medium, channel, nextSequence, payload)
                nextSequence = (nextSequence + 1) and 0xFFFFFFFFL
                output.write(frame)
                output.flush()
            }
        }
    }

    /**
     * Flujo de mensajes entrantes; termina cuando el par cierra la conexión. Debe recolectarse una sola vez.
     * Cancelar la recolección cierra el socket, porque una lectura bloqueante de `java.net` no responde a la cancelación.
     */
    fun incoming(): Flow<ChannelMessage> = flow {
        coroutineScope {
            // [DECISIÓN TÉCNICA]: El vigilante cierra el socket al cancelarse el ámbito para desbloquear `read`; sin él, el hilo quedaría bloqueado hasta que el par cerrara.
            val watcher = launch {
                try {
                    awaitCancellation()
                } finally {
                    close()
                }
            }
            val input = socket.getInputStream()
            val reassembler = FrameReassembler(rustCore)
            val chunk = ByteArray(READ_CHUNK_BYTES)
            while (true) {
                val read = try {
                    input.read(chunk)
                } catch (error: IOException) {
                    -1
                }
                if (read < 0) {
                    break
                }
                reassembler.feed(chunk, read)
                for (frame in reassembler.drainValidFrames()) {
                    val message = try {
                        ChannelMessageCodec.decode(frame.payload)
                    } catch (error: ChannelProtocolException) {
                        malformedMessages++
                        null
                    }
                    if (message != null) {
                        emit(message)
                    }
                }
            }
            watcher.cancel()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Cierra el socket; las lecturas pendientes terminan y el flujo [incoming] se completa.
     */
    override fun close() {
        runCatching { socket.close() }
    }

    companion object {
        private const val READ_CHUNK_BYTES = 16 * 1024

        /**
         * Abre una conexión TCP hacia el ordenador (por ejemplo `127.0.0.1` cuando existe `adb reverse`).
         */
        suspend fun connect(
            host: String,
            port: Int,
            rustCore: RustCoreBridgeContract,
            medium: TransportType,
            timeoutMs: Int = UpiicsaLinkDefaults.CONNECT_TIMEOUT_MS
        ): FrameSocketChannel = withContext(Dispatchers.IO) {
            val socket = Socket()
            try {
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(host, port), timeoutMs)
            } catch (error: IOException) {
                socket.close()
                throw error
            }
            FrameSocketChannel(socket, rustCore, medium)
        }
    }
}
