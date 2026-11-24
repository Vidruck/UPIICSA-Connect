package mx.ipn.upiicsa.connect.android.model

import android.os.Build
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import mx.ipn.upiicsa.connect.shared.link.FrameSocketChannel
import mx.ipn.upiicsa.connect.shared.link.UpiicsaLinkDefaults
import mx.ipn.upiicsa.connect.shared.model.ConnectionDirection
import mx.ipn.upiicsa.connect.shared.model.NotificationResolutionAction
import mx.ipn.upiicsa.connect.shared.model.PhoneNotificationItem
import mx.ipn.upiicsa.connect.shared.model.RemoteDeviceDescriptor
import mx.ipn.upiicsa.connect.shared.model.TransportType
import mx.ipn.upiicsa.connect.shared.protocol.ChannelMessage
import mx.ipn.upiicsa.connect.shared.protocol.ContentOrigin
import mx.ipn.upiicsa.connect.shared.protocol.RustCoreBridgeContract
import java.io.IOException

/**
 * Evento recibido del ordenador por el enlace real.
 */
sealed class DesktopLinkEvent {

    /**
     * Saludo del ordenador con el PIN que también muestra su pantalla.
     */
    data class Hello(val desktopName: String, val pairingPin: Int) : DesktopLinkEvent()

    /**
     * Texto copiado en el ordenador.
     */
    data class ClipboardFromDesktop(val text: String) : DesktopLinkEvent()

    /**
     * Resolución de notificación ordenada desde el ordenador.
     */
    data class Resolution(val action: NotificationResolutionAction) : DesktopLinkEvent()

    /**
     * Latido del ordenador ya devuelto (eco); cuenta las tramas recibidas.
     */
    data object Heartbeat : DesktopLinkEvent()

    /**
     * El ordenador cerró la conexión o el socket falló.
     */
    data class Closed(val reason: String) : DesktopLinkEvent()
}

/**
 * Repositorio (capa Model) del enlace real con el ordenador por TCP. Con el cable USB y `adb reverse` activo en la PC,
 * el teléfono alcanza el servidor de escritorio en `127.0.0.1:47800`.
 */
class AndroidSocketLinkRepository(
    private val rustCore: RustCoreBridgeContract,
    private val host: String = LOOPBACK_HOST,
    private val port: Int = UpiicsaLinkDefaults.TCP_PORT
) {

    @Volatile
    private var channel: FrameSocketChannel? = null

    /**
     * Descriptor del ordenador alcanzable por el túnel USB.
     */
    val usbDesktop: RemoteDeviceDescriptor = RemoteDeviceDescriptor(
        deviceId = "pc-adb-reverse",
        deviceName = "Ordenador por cable USB (adb reverse)",
        modelName = "Escritorio UPIICSA-Connect",
        androidSdkVersion = null,
        availableTransports = listOf(TransportType.USB_CABLE),
        preferredTransport = TransportType.USB_CABLE
    )

    /**
     * Abre la conexión TCP y envía el saludo del teléfono; retorna el error legible si no fue posible.
     */
    suspend fun connect(direction: ConnectionDirection, deviceName: String = Build.MODEL ?: "Android"): String? {
        disconnect()
        return try {
            val link = FrameSocketChannel.connect(host, port, rustCore, TransportType.USB_CABLE)
            channel = link
            link.send(ChannelMessage.HandshakeHello(direction, deviceName, pairingPin = 0))
            null
        } catch (error: IOException) {
            disconnect()
            "No se pudo abrir $host:$port (${error.message ?: error::class.simpleName})"
        }
    }

    /**
     * Flujo de eventos del ordenador; devuelve cada latido como eco para que la PC mida la latencia.
     */
    fun events(): Flow<DesktopLinkEvent> = flow {
        val link = channel
        if (link == null) {
            emit(DesktopLinkEvent.Closed("Sin conexión con el ordenador"))
            return@flow
        }
        link.incoming().collect { message ->
            when (message) {
                is ChannelMessage.Heartbeat -> {
                    sendQuietly(message)
                    emit(DesktopLinkEvent.Heartbeat)
                }
                is ChannelMessage.HandshakeHello -> emit(DesktopLinkEvent.Hello(message.deviceName, message.pairingPin))
                is ChannelMessage.ClipboardText ->
                    if (message.origin == ContentOrigin.DESKTOP) emit(DesktopLinkEvent.ClipboardFromDesktop(message.text))
                is ChannelMessage.NotificationReply ->
                    emit(DesktopLinkEvent.Resolution(NotificationResolutionAction.QuickReply(message.notificationId, message.replyText)))
                is ChannelMessage.NotificationMarkRead ->
                    emit(DesktopLinkEvent.Resolution(NotificationResolutionAction.MarkAsRead(message.notificationId)))
                is ChannelMessage.NotificationDismiss ->
                    emit(DesktopLinkEvent.Resolution(NotificationResolutionAction.Dismiss(message.notificationId)))
                else -> Unit
            }
        }
        channel = null
        emit(DesktopLinkEvent.Closed("El ordenador cerró la conexión"))
    }

    /**
     * Espeja una notificación del teléfono hacia el ordenador.
     */
    suspend fun publishNotification(item: PhoneNotificationItem) {
        sendQuietly(
            ChannelMessage.NotificationPosted(
                notificationId = item.notificationId,
                appName = item.appName,
                packageName = item.packageName,
                senderTitle = item.senderTitle,
                messageBody = item.messageBody,
                supportsInlineReply = item.supportsInlineReply
            )
        )
    }

    /**
     * Envía al ordenador el texto copiado en el teléfono.
     */
    suspend fun publishClipboard(text: String) {
        sendQuietly(ChannelMessage.ClipboardText(ContentOrigin.MOBILE, text))
    }

    /**
     * Indica si hay una conexión abierta con el ordenador.
     */
    val isConnected: Boolean
        get() = channel?.isOpen == true

    /**
     * Cierra la conexión con el ordenador.
     */
    fun disconnect() {
        channel?.close()
        channel = null
    }

    /**
     * Envía un mensaje ignorando fallas de escritura; el cierre se detecta en [events].
     */
    private suspend fun sendQuietly(message: ChannelMessage) {
        val link = channel ?: return
        try {
            link.send(message)
        } catch (error: IOException) {
            link.close()
        }
    }

    private companion object {
        const val LOOPBACK_HOST = "127.0.0.1"
    }
}
