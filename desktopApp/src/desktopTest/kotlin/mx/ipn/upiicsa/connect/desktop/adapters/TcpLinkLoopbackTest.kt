package mx.ipn.upiicsa.connect.desktop.adapters

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mx.ipn.upiicsa.connect.desktop.adapters.process.ProcessResult
import mx.ipn.upiicsa.connect.desktop.adapters.transport.tcp.AdbDeviceBridge
import mx.ipn.upiicsa.connect.desktop.adapters.transport.tcp.SocketPhoneEventStreamAdapter
import mx.ipn.upiicsa.connect.desktop.adapters.transport.tcp.TcpLinkSession
import mx.ipn.upiicsa.connect.desktop.adapters.transport.tcp.TcpLinkTransportAdapter
import mx.ipn.upiicsa.connect.desktop.application.DesktopConnectionService
import mx.ipn.upiicsa.connect.desktop.domain.DesktopSessionAggregate
import mx.ipn.upiicsa.connect.desktop.ports.outbound.OsAudioSinkOutboundPort
import mx.ipn.upiicsa.connect.desktop.ports.outbound.OsClipboardOutboundPort
import mx.ipn.upiicsa.connect.desktop.ports.outbound.OsNotificationOutboundPort
import mx.ipn.upiicsa.connect.desktop.ports.outbound.OsVirtualCameraMicOutboundPort
import mx.ipn.upiicsa.connect.desktop.ports.outbound.OsVirtualDisplayOutboundPort
import mx.ipn.upiicsa.connect.shared.link.FrameSocketChannel
import mx.ipn.upiicsa.connect.shared.model.ConnectionDirection
import mx.ipn.upiicsa.connect.shared.model.ConnectionStatus
import mx.ipn.upiicsa.connect.shared.model.NotificationResolutionAction
import mx.ipn.upiicsa.connect.shared.model.PhoneNotificationItem
import mx.ipn.upiicsa.connect.shared.model.RemoteDeviceDescriptor
import mx.ipn.upiicsa.connect.shared.model.SecondaryDisplayConfig
import mx.ipn.upiicsa.connect.shared.model.TransportType
import mx.ipn.upiicsa.connect.shared.protocol.ChannelMessage
import mx.ipn.upiicsa.connect.shared.protocol.ContentOrigin
import mx.ipn.upiicsa.connect.shared.protocol.DummyRustCoreBridge
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Prueba extremo a extremo del enlace real por TCP en bucle local: el servicio de escritorio con sus adaptadores reales
 * y un cliente JVM que actúa como la app Android usando el mismo `FrameSocketChannel` compartido.
 */
class TcpLinkLoopbackTest {

    /**
     * Puertos del sistema operativo en memoria para observar lo que el servicio publica.
     */
    private class InMemoryOs : OsNotificationOutboundPort, OsClipboardOutboundPort, OsVirtualDisplayOutboundPort,
        OsVirtualCameraMicOutboundPort, OsAudioSinkOutboundPort {
        val published = mutableListOf<PhoneNotificationItem>()

        @Volatile
        var clipboard = ""

        /**
         * Registra la notificación espejada.
         */
        override suspend fun publishPhoneNotification(item: PhoneNotificationItem) {
            published += item
        }

        /**
         * Sin efecto en la prueba.
         */
        override suspend fun dispatchResolutionFeedback(action: NotificationResolutionAction) = Unit

        /**
         * Guarda el texto escrito en el portapapeles simulado.
         */
        override suspend fun writeClipboardText(text: String) {
            clipboard = text
        }

        /**
         * Devuelve el texto del portapapeles simulado.
         */
        override suspend fun readClipboardText(): String = clipboard

        /**
         * Sin efecto en la prueba.
         */
        override suspend fun startVirtualDisplay(config: SecondaryDisplayConfig): Boolean = true

        /**
         * Sin efecto en la prueba.
         */
        override suspend fun stopVirtualDisplay() = Unit

        /**
         * Cuadro vacío.
         */
        override suspend fun captureCurrentFrame(): ByteArray = ByteArray(0)

        /**
         * Sin efecto en la prueba.
         */
        override suspend fun enableVirtualDevices(enableCamera: Boolean, enableMicrophone: Boolean): Boolean = true

        /**
         * Sin efecto en la prueba.
         */
        override suspend fun pushVideoFrame(frameBytes: ByteArray) = Unit

        /**
         * Sin efecto en la prueba.
         */
        override suspend fun pushMicAudio(pcmSamples: ShortArray) = Unit

        /**
         * Sin efecto en la prueba.
         */
        override suspend fun openMusicModeSink(sampleRateHz: Int): Boolean = true

        /**
         * Sin efecto en la prueba.
         */
        override suspend fun writePcmSamples(samples: ShortArray) = Unit

        /**
         * Sin efecto en la prueba.
         */
        override suspend fun closeMusicModeSink() = Unit
    }

    private val core = DummyRustCoreBridge()
    private val session = TcpLinkSession(core, requestedPort = 0)
    private val os = InMemoryOs()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val service = DesktopConnectionService(
        aggregate = DesktopSessionAggregate(),
        rustCore = core,
        transportPort = TcpLinkTransportAdapter(
            session,
            AdbDeviceBridge(runner = { _, _, _ -> ProcessResult(0, "List of devices attached\n", "") }),
            acceptTimeoutMs = 5_000
        ),
        notificationPort = os,
        clipboardPort = os,
        virtualDisplayPort = os,
        virtualCameraMicPort = os,
        audioSinkPort = os,
        phoneEventPort = SocketPhoneEventStreamAdapter(session, heartbeatIntervalMs = 100, heartbeatTimeoutMs = 2_000),
        localDeviceId = "pc-loopback"
    )

    /**
     * Libera el servidor y las corrutinas de la prueba.
     */
    @AfterTest
    fun liberar() {
        scope.cancel()
        session.close()
    }

    /**
     * Recorre saludo, PIN, latidos, notificación entrante, respuesta, portapapeles en ambos sentidos y cierre del enlace.
     */
    @Test
    fun enlaceRealCompletoEnBucleLocal() = runBlocking {
        withTimeout(20_000) {
            val port = session.listen()
            scope.launch { service.runPhoneEventLoop() }

            val desktopConnect = scope.async {
                service.connectToDevice(TELEFONO_LOOPBACK, TransportType.USB_CABLE, ConnectionDirection.MOBILE_TO_DESKTOP)
            }
            val phone = connectPhone(port)
            val fromDesktop = Channel<ChannelMessage>(Channel.UNLIMITED)
            scope.launch {
                phone.incoming().collect { message ->
                    if (message is ChannelMessage.Heartbeat) {
                        phone.send(message)
                    } else {
                        fromDesktop.send(message)
                    }
                }
            }

            val connected = desktopConnect.await()
            assertEquals(ConnectionStatus.CONNECTED, connected.status)
            val hello = fromDesktop.receive() as ChannelMessage.HandshakeHello
            assertEquals(connected.pairingPin, hello.pairingPin)

            phone.send(ChannelMessage.NotificationPosted("n-1", "WhatsApp", "com.whatsapp", "Equipo", "¿Listos?", true))
            service.sessionState.first { state -> state.notifications.any { it.notificationId == "n-1" } }
            assertEquals("Equipo", os.published.single().senderTitle)

            service.resolveNotification(NotificationResolutionAction.QuickReply("n-1", "Sí, en 5 min"))
            assertEquals(ChannelMessage.NotificationReply("n-1", "Sí, en 5 min"), fromDesktop.receive())

            phone.send(ChannelMessage.ClipboardText(ContentOrigin.MOBILE, "Texto desde el teléfono"))
            service.sessionState.first { it.sharedClipboardText == "Texto desde el teléfono" }
            assertEquals("Texto desde el teléfono", os.clipboard)

            service.pushClipboardText("Texto desde la PC")
            assertEquals(ChannelMessage.ClipboardText(ContentOrigin.DESKTOP, "Texto desde la PC"), fromDesktop.receive())

            service.sessionState.first { it.linkTelemetry.framesReceived >= 3 }

            phone.close()
            val closed = service.sessionState.first { it.status == ConnectionStatus.DISCONNECTED }
            assertTrue(closed.activityLog.any { it.message.startsWith("Enlace cerrado") })
        }
    }

    /**
     * Verifica que sin teléfono la conexión falle tras el tiempo de espera en lugar de bloquearse.
     */
    @Test
    fun conexionSinTelefonoFallaPorTiempo() = runBlocking {
        val fastTransport = TcpLinkTransportAdapter(
            session,
            AdbDeviceBridge(runner = { _, _, _ -> null }),
            acceptTimeoutMs = 300
        )
        withTimeout(5_000) {
            val accepted = fastTransport.connect(TELEFONO_LOOPBACK, TransportType.USB_CABLE, ConnectionDirection.MOBILE_TO_DESKTOP)
            assertEquals(false, accepted)
        }
    }

    /**
     * Conecta el cliente que actúa como teléfono reintentando mientras el servidor empieza a aceptar.
     */
    private suspend fun connectPhone(port: Int): FrameSocketChannel {
        repeat(50) {
            try {
                return FrameSocketChannel.connect("127.0.0.1", port, core, TransportType.USB_CABLE)
            } catch (error: IOException) {
                delay(50)
            }
        }
        error("El servidor de escritorio no aceptó la conexión")
    }

    private companion object {
        val TELEFONO_LOOPBACK = RemoteDeviceDescriptor(
            deviceId = "loopback-telefono",
            deviceName = "Teléfono de prueba (loopback)",
            modelName = "JVM",
            androidSdkVersion = null,
            availableTransports = listOf(TransportType.USB_CABLE),
            preferredTransport = TransportType.USB_CABLE
        )
    }
}
