package mx.ipn.upiicsa.connect.android.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import mx.ipn.upiicsa.connect.android.model.AndroidDeviceRepository
import mx.ipn.upiicsa.connect.android.model.AndroidMediaStreamRepository
import mx.ipn.upiicsa.connect.android.model.AndroidPairingRepository
import mx.ipn.upiicsa.connect.android.model.AndroidSocketLinkRepository
import mx.ipn.upiicsa.connect.android.model.NotificationBridgeSource
import mx.ipn.upiicsa.connect.shared.link.FrameSocketChannel
import mx.ipn.upiicsa.connect.shared.model.ConnectionDirection
import mx.ipn.upiicsa.connect.shared.model.ConnectionStatus
import mx.ipn.upiicsa.connect.shared.model.NotificationResolutionAction
import mx.ipn.upiicsa.connect.shared.model.PhoneNotificationItem
import mx.ipn.upiicsa.connect.shared.model.TransportType
import mx.ipn.upiicsa.connect.shared.protocol.ChannelMessage
import mx.ipn.upiicsa.connect.shared.protocol.ContentOrigin
import mx.ipn.upiicsa.connect.shared.protocol.DummyRustCoreBridge
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Fuente falsa en memoria para probar el flujo completo del ViewModel Android con enlace TCP real sin dispositivo.
 */
private class FakeNotificationBridgeSource : NotificationBridgeSource {

    val incoming = MutableSharedFlow<PhoneNotificationItem>(replay = 1, extraBufferCapacity = 8)
    val resolvedActions = CopyOnWriteArrayList<NotificationResolutionAction>()
    private val items = CopyOnWriteArrayList<PhoneNotificationItem>()

    /**
     * Devuelve la instantánea de notificaciones activas en memoria.
     */
    override suspend fun loadActiveNotifications(): List<PhoneNotificationItem> = items.toList()

    /**
     * Registra la acción enviada desde la PC y marca como resuelta la notificación correspondiente.
     */
    override suspend fun resolveNotificationFromDesktop(action: NotificationResolutionAction): List<PhoneNotificationItem> {
        resolvedActions.add(action)
        val targetId = when (action) {
            is NotificationResolutionAction.QuickReply -> action.notificationId
            is NotificationResolutionAction.MarkAsRead -> action.notificationId
            is NotificationResolutionAction.Dismiss -> action.notificationId
        }
        val updated = items.map { item ->
            if (item.notificationId == targetId) {
                item.copy(isResolved = true, resolutionSummary = "Aplicada en prueba")
            } else {
                item
            }
        }
        items.clear()
        items.addAll(updated)
        return items.toList()
    }

    /**
     * Emite notificaciones simuladas como si provinieran del servicio del sistema.
     */
    override fun incomingNotifications(): Flow<PhoneNotificationItem> = incoming

    /**
     * Inserta y emite una notificación nueva en el flujo observado por el ViewModel.
     */
    suspend fun emitPosted(item: PhoneNotificationItem) {
        items.add(0, item)
        incoming.emit(item)
    }
}

/**
 * Pruebas de integración JVM del [AndroidConnectionViewModel] conectado a un servidor TCP loopback.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidConnectionViewModelRealTest {

    private val core = DummyRustCoreBridge()

    /**
     * Configura el despachador principal para pruebas de ViewModel fuera de Android.
     */
    @BeforeTest
    fun configurarMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    /**
     * Restaura el despachador principal al terminar cada prueba.
     */
    @AfterTest
    fun restaurarMain() {
        Dispatchers.resetMain()
    }

    /**
     * Verifica el flujo real de notificación nueva -> PC, respuesta desde PC y portapapeles bidireccional.
     */
    @Test
    fun flujoRealNotificacionesYPortapapelesEnViewModel(): Unit = runBlocking {
        ServerSocket().use { server ->
            server.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
            val fakeSource = FakeNotificationBridgeSource()
            val socketRepo = AndroidSocketLinkRepository(core, host = "127.0.0.1", port = server.localPort)
            val viewModel = AndroidConnectionViewModel(
                deviceRepository = AndroidDeviceRepository(core),
                notificationRepository = fakeSource,
                mediaStreamRepository = AndroidMediaStreamRepository(core),
                pairingRepository = AndroidPairingRepository(core),
                socketLinkRepository = socketRepo
            )

            withTimeout(10_000) {
                val accepted = async(Dispatchers.IO) { server.accept() }
                viewModel.setRealLinkEnabled(true)
                viewModel.connectToDesktop(socketRepo.usbDesktop)
                val desktopChannel = FrameSocketChannel(accepted.await(), core, TransportType.USB_CABLE)
                val received = CopyOnWriteArrayList<ChannelMessage>()
                val readerJob = launch {
                    desktopChannel.incoming().collect { received.add(it) }
                }

                desktopChannel.send(ChannelMessage.HandshakeHello(ConnectionDirection.MOBILE_TO_DESKTOP, "PC-Prueba", 654_321))
                desktopChannel.send(ChannelMessage.ClipboardText(ContentOrigin.DESKTOP, "copiado en escritorio"))

                while (viewModel.uiState.value.pairingPin != 654_321 ||
                    viewModel.uiState.value.clipboardText != "copiado en escritorio"
                ) {
                    delay(20)
                }

                viewModel.syncClipboard("copiado en telefono")
                val posted = PhoneNotificationItem(
                    notificationId = "n-real-1",
                    appName = "WhatsApp",
                    packageName = "com.whatsapp",
                    senderTitle = "Equipo",
                    messageBody = "Probando enlace",
                    timestampLabel = "17:35",
                    supportsInlineReply = true
                )
                fakeSource.emitPosted(posted)

                while (received.size < 3) {
                    delay(20)
                }

                assertEquals(ChannelMessage.HandshakeHello::class, received[0]::class)
                assertTrue(received.contains(ChannelMessage.ClipboardText(ContentOrigin.MOBILE, "copiado en telefono")))
                assertTrue(
                    received.contains(
                        ChannelMessage.NotificationPosted(
                            notificationId = "n-real-1",
                            appName = "WhatsApp",
                            packageName = "com.whatsapp",
                            senderTitle = "Equipo",
                            messageBody = "Probando enlace",
                            supportsInlineReply = true
                        )
                    )
                )

                desktopChannel.send(ChannelMessage.NotificationReply("n-real-1", "Confirmado"))
                while (fakeSource.resolvedActions.isEmpty()) {
                    delay(20)
                }

                assertEquals(ConnectionStatus.CONNECTED, viewModel.uiState.value.status)
                assertEquals(654_321, viewModel.uiState.value.pairingPin)
                assertEquals("copiado en telefono", viewModel.uiState.value.clipboardText)
                assertTrue(viewModel.uiState.value.clipboardHistory.any { !it.fromMobile && it.text == "copiado en escritorio" })
                assertTrue(viewModel.uiState.value.clipboardHistory.any { it.fromMobile && it.text == "copiado en telefono" })
                assertEquals(
                    listOf<NotificationResolutionAction>(NotificationResolutionAction.QuickReply("n-real-1", "Confirmado")),
                    fakeSource.resolvedActions.toList()
                )
                assertTrue(viewModel.uiState.value.notifications.first { it.notificationId == "n-real-1" }.isResolved)

                readerJob.cancel()
                desktopChannel.close()
                viewModel.disconnectDesktop()
            }
        }
    }
}
