package mx.ipn.upiicsa.connect.desktop.application

import kotlinx.coroutines.test.runTest
import mx.ipn.upiicsa.connect.desktop.adapters.simulation.DummyPhoneEventStreamAdapter
import mx.ipn.upiicsa.connect.desktop.domain.DesktopSessionAggregate
import mx.ipn.upiicsa.connect.desktop.domain.PhoneEvent
import mx.ipn.upiicsa.connect.desktop.ports.outbound.DeviceTransportOutboundPort
import mx.ipn.upiicsa.connect.desktop.ports.outbound.OsAudioSinkOutboundPort
import mx.ipn.upiicsa.connect.desktop.ports.outbound.OsClipboardOutboundPort
import mx.ipn.upiicsa.connect.desktop.ports.outbound.OsNotificationOutboundPort
import mx.ipn.upiicsa.connect.desktop.ports.outbound.OsVirtualCameraMicOutboundPort
import mx.ipn.upiicsa.connect.desktop.ports.outbound.OsVirtualDisplayOutboundPort
import mx.ipn.upiicsa.connect.shared.model.ConnectionDirection
import mx.ipn.upiicsa.connect.shared.model.ConnectionStatus
import mx.ipn.upiicsa.connect.shared.model.NotificationResolutionAction
import mx.ipn.upiicsa.connect.shared.model.PhoneNotificationItem
import mx.ipn.upiicsa.connect.shared.model.RemoteDeviceDescriptor
import mx.ipn.upiicsa.connect.shared.model.SecondaryDisplayConfig
import mx.ipn.upiicsa.connect.shared.model.TransportType
import mx.ipn.upiicsa.connect.shared.protocol.ChannelMessage
import mx.ipn.upiicsa.connect.shared.protocol.ChannelMessageCodec
import mx.ipn.upiicsa.connect.shared.protocol.ContentOrigin
import mx.ipn.upiicsa.connect.shared.protocol.DummyRustCoreBridge
import mx.ipn.upiicsa.connect.shared.protocol.ServiceChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pruebas del servicio de aplicación hexagonal usando puertos de salida en memoria.
 */
class DesktopConnectionServiceTest {

    /**
     * Transporte en memoria que registra cada carga útil enviada por canal.
     */
    private class RecordingTransport : DeviceTransportOutboundPort {
        val sent = mutableListOf<Pair<ServiceChannel, ByteArray>>()
        var connected = false

        /**
         * Devuelve dos dispositivos fijos de prueba.
         */
        override suspend fun discoverDevices(): List<RemoteDeviceDescriptor> = listOf(TELEFONO_WIFI, TELEFONO_USB)
        /**
         * Marca el transporte como conectado.
         */
        override suspend fun connect(device: RemoteDeviceDescriptor, transport: TransportType, direction: ConnectionDirection): Boolean {
            connected = true
            return true
        }
        /**
         * Marca el transporte como desconectado.
         */
        override suspend fun disconnect() {
            connected = false
        }
        /**
         * Registra la carga útil enviada y acepta solo con enlace activo.
         */
        override suspend fun sendChannelPayload(channel: ServiceChannel, payload: ByteArray): Boolean {
            sent += channel to payload
            return connected
        }
    }

    /**
     * Adaptadores del sistema operativo en memoria que registran las llamadas recibidas.
     */
    private class RecordingOs : OsNotificationOutboundPort, OsClipboardOutboundPort, OsVirtualDisplayOutboundPort,
        OsVirtualCameraMicOutboundPort, OsAudioSinkOutboundPort {
        val published = mutableListOf<PhoneNotificationItem>()
        var clipboard = ""
        var sinkOpen = false
        var pcmWrites = 0
        var displayActive = false

        /**
         * Registra la notificación publicada.
         */
        override suspend fun publishPhoneNotification(item: PhoneNotificationItem) { published += item }
        /**
         * Ignora el acuse visual de resolución.
         */
        override suspend fun dispatchResolutionFeedback(action: NotificationResolutionAction) = Unit
        /**
         * Guarda el texto del portapapeles en memoria.
         */
        override suspend fun writeClipboardText(text: String) { clipboard = text }
        /**
         * Devuelve el texto guardado en memoria.
         */
        override suspend fun readClipboardText(): String = clipboard
        /**
         * Marca el monitor virtual como activo.
         */
        override suspend fun startVirtualDisplay(config: SecondaryDisplayConfig): Boolean { displayActive = true; return true }
        /**
         * Marca el monitor virtual como inactivo.
         */
        override suspend fun stopVirtualDisplay() { displayActive = false }
        /**
         * Devuelve un cuadro fijo de 3 bytes.
         */
        override suspend fun captureCurrentFrame(): ByteArray = byteArrayOf(1, 2, 3)
        /**
         * Acepta la activación de dispositivos virtuales.
         */
        override suspend fun enableVirtualDevices(enableCamera: Boolean, enableMicrophone: Boolean): Boolean = true
        /**
         * Ignora el cuadro de video recibido.
         */
        override suspend fun pushVideoFrame(frameBytes: ByteArray) = Unit
        /**
         * Ignora el audio de micrófono recibido.
         */
        override suspend fun pushMicAudio(pcmSamples: ShortArray) = Unit
        /**
         * Marca el sumidero de audio como abierto.
         */
        override suspend fun openMusicModeSink(sampleRateHz: Int): Boolean { sinkOpen = true; return true }
        /**
         * Cuenta las escrituras PCM recibidas.
         */
        override suspend fun writePcmSamples(samples: ShortArray) { pcmWrites++ }
        /**
         * Marca el sumidero de audio como cerrado.
         */
        override suspend fun closeMusicModeSink() { sinkOpen = false }
    }

    private val transport = RecordingTransport()
    private val os = RecordingOs()
    private val service = DesktopConnectionService(
        aggregate = DesktopSessionAggregate(),
        rustCore = DummyRustCoreBridge(),
        transportPort = transport,
        notificationPort = os,
        clipboardPort = os,
        virtualDisplayPort = os,
        virtualCameraMicPort = os,
        audioSinkPort = os,
        phoneEventPort = DummyPhoneEventStreamAdapter(),
        localDeviceId = "pc-linux-upiicsa",
        clock = { 1_260_000L }
    )

    /**
     * Verifica el orden por prioridad USB, el PIN derivado del core y el saludo enviado por el canal de control.
     */
    @Test
    fun conexionDerivaPinYEnviaSaludo() = runTest {
        val devices = service.discoverAvailableDevices()
        assertEquals(TransportType.USB_CABLE, devices.first().preferredTransport)

        val state = service.connectToDevice(TELEFONO_USB, null, ConnectionDirection.MOBILE_TO_DESKTOP)
        assertEquals(ConnectionStatus.CONNECTED, state.status)
        assertEquals(TransportType.USB_CABLE, state.activeTransport)
        val expectedPin = DummyRustCoreBridge().derivePairingPin("pc-linux-upiicsa", TELEFONO_USB.deviceId, 42)
        assertEquals(expectedPin, state.pairingPin)

        val (channel, payload) = transport.sent.single()
        assertEquals(ServiceChannel.SESSION_CONTROL, channel)
        val hello = ChannelMessageCodec.decode(payload) as ChannelMessage.HandshakeHello
        assertEquals(expectedPin, hello.pairingPin)
        assertEquals(1L, service.sessionState.value.linkTelemetry.framesSent)
    }

    /**
     * Verifica que la respuesta rápida se codifique con el códec tipado y marque la notificación como resuelta.
     */
    @Test
    fun respuestaRapidaViajaCodificadaYResuelve() = runTest {
        service.connectToDevice(TELEFONO_USB, TransportType.WIFI_LAN, ConnectionDirection.DESKTOP_TO_MOBILE)
        service.processPhoneEvent(PhoneEvent.IncomingNotification(NOTIFICACION))
        assertEquals(1, service.sessionState.value.pendingNotificationCount)
        assertEquals(NOTIFICACION, os.published.single())

        service.resolveNotification(NotificationResolutionAction.QuickReply("n-1", "Voy | en camino"))
        val (channel, payload) = transport.sent.last()
        assertEquals(ServiceChannel.NOTIFICATION_RESOLUTION, channel)
        assertEquals(ChannelMessage.NotificationReply("n-1", "Voy | en camino"), ChannelMessageCodec.decode(payload))
        assertEquals(0, service.sessionState.value.pendingNotificationCount)
    }

    /**
     * Verifica que los eventos del teléfono se ignoren sin sesión y respeten los interruptores de servicio.
     */
    @Test
    fun eventosRespetanSesionEInterruptores() = runTest {
        service.processPhoneEvent(PhoneEvent.ClipboardFromPhone("ignorado"))
        assertEquals("", os.clipboard)

        service.connectToDevice(TELEFONO_USB, null, ConnectionDirection.DESKTOP_TO_MOBILE)
        service.processPhoneEvent(PhoneEvent.ClipboardFromPhone("desde Android"))
        assertEquals("desde Android", os.clipboard)
        assertTrue(service.sessionState.value.clipboardHistory.first().fromMobile)

        service.setClipboardSync(false)
        service.processPhoneEvent(PhoneEvent.ClipboardFromPhone("pausado"))
        assertEquals("desde Android", os.clipboard)

        service.processPhoneEvent(PhoneEvent.MusicPcmBlock(1, shortArrayOf(1000, -1000), "Pista"))
        assertEquals(0, os.pcmWrites)
        service.toggleMusicMode(true, 1.5f, "Pista")
        service.processPhoneEvent(PhoneEvent.MusicPcmBlock(2, shortArrayOf(10_000, -10_000), "Pista"))
        assertEquals(1, os.pcmWrites)
        assertTrue(service.sessionState.value.mediaTelemetry.musicLevel > 0.4f)
    }

    /**
     * Verifica el envío de cuadros del monitor secundario y la liberación completa al desconectar.
     */
    @Test
    fun monitorSecundarioYDesconexion() = runTest {
        service.connectToDevice(TELEFONO_USB, null, ConnectionDirection.DESKTOP_TO_MOBILE)
        service.toggleSecondaryDisplay(SecondaryDisplayConfig(enabled = true))
        service.processPhoneEvent(PhoneEvent.DisplayVsync(1))
        assertEquals(1L, service.sessionState.value.mediaTelemetry.displayFramesSent)
        assertTrue(os.displayActive)

        service.pushClipboardText("hola")
        val clip = ChannelMessageCodec.decode(transport.sent.last().second)
        assertEquals(ChannelMessage.ClipboardText(ContentOrigin.DESKTOP, "hola"), clip)

        val state = service.disconnect()
        assertEquals(ConnectionStatus.DISCONNECTED, state.status)
        assertNull(state.pairingPin)
        assertTrue(!os.displayActive && !os.sinkOpen && !transport.connected)
        assertNotNull(state.activityLog.lastOrNull())
    }

    private companion object {
        val TELEFONO_USB = RemoteDeviceDescriptor(
            deviceId = "usb-pixel8-upiicsa",
            deviceName = "Pixel 8 Pro",
            modelName = "Google Pixel 8 Pro",
            androidSdkVersion = 34,
            availableTransports = listOf(TransportType.USB_CABLE, TransportType.WIFI_LAN),
            preferredTransport = TransportType.USB_CABLE
        )
        val TELEFONO_WIFI = TELEFONO_USB.copy(
            deviceId = "wifi-tab",
            availableTransports = listOf(TransportType.WIFI_LAN),
            preferredTransport = TransportType.WIFI_LAN
        )
        val NOTIFICACION = PhoneNotificationItem(
            notificationId = "n-1",
            appName = "WhatsApp",
            packageName = "com.whatsapp",
            senderTitle = "Equipo",
            messageBody = "¿Listo?",
            timestampLabel = "10:00",
            supportsInlineReply = true
        )
    }
}
