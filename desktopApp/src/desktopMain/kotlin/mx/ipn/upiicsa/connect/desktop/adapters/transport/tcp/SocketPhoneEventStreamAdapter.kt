package mx.ipn.upiicsa.connect.desktop.adapters.transport.tcp

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mx.ipn.upiicsa.connect.desktop.domain.PhoneEvent
import mx.ipn.upiicsa.connect.desktop.ports.outbound.PhoneEventStreamOutboundPort
import mx.ipn.upiicsa.connect.shared.link.UpiicsaLinkDefaults
import mx.ipn.upiicsa.connect.shared.model.PhoneNotificationItem
import mx.ipn.upiicsa.connect.shared.model.RemoteDeviceDescriptor
import mx.ipn.upiicsa.connect.shared.model.TransportType
import mx.ipn.upiicsa.connect.shared.protocol.ChannelMessage
import mx.ipn.upiicsa.connect.shared.protocol.ChannelMessageCodec
import mx.ipn.upiicsa.connect.shared.protocol.ContentOrigin
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Convierte los mensajes recibidos por el socket del teléfono en [PhoneEvent] y mantiene el enlace vivo con latidos:
 * el ordenador envía `Heartbeat(t)`, el teléfono lo devuelve y la latencia se estima como la mitad del viaje redondo.
 */
class SocketPhoneEventStreamAdapter(
    private val session: TcpLinkSession,
    private val clock: () -> Long = System::currentTimeMillis,
    private val heartbeatIntervalMs: Long = UpiicsaLinkDefaults.HEARTBEAT_INTERVAL_MS,
    private val heartbeatTimeoutMs: Long = UpiicsaLinkDefaults.HEARTBEAT_TIMEOUT_MS
) : PhoneEventStreamOutboundPort {

    /**
     * Flujo de eventos del teléfono conectado; emite [PhoneEvent.LinkClosed] al cerrarse el socket, vencer los latidos o
     * recibir un saludo con otra versión de protocolo.
     */
    override fun events(device: RemoteDeviceDescriptor, transport: TransportType): Flow<PhoneEvent> = channelFlow {
        val link = session.activeChannel()
        if (link == null) {
            send(PhoneEvent.LinkClosed("No hay un teléfono conectado al servidor TCP"))
            return@channelFlow
        }
        val lastInbound = AtomicLong(clock())
        val closeReason = AtomicReference("El teléfono cerró la conexión")
        val heartbeats = launch {
            while (isActive) {
                delay(heartbeatIntervalMs)
                if (clock() - lastInbound.get() > heartbeatTimeoutMs) {
                    closeReason.set("Sin latidos del teléfono durante ${heartbeatTimeoutMs / 1000} s")
                    link.close()
                    break
                }
                try {
                    link.send(ChannelMessage.Heartbeat(clock()))
                } catch (error: IOException) {
                    link.close()
                    break
                }
            }
        }
        link.incoming().collect { message ->
            lastInbound.set(clock())
            if (message is ChannelMessage.HandshakeHello && message.protocolVersion != ChannelMessageCodec.PROTOCOL_VERSION) {
                closeReason.set(
                    "Versión de protocolo incompatible (teléfono v${message.protocolVersion}, " +
                        "ordenador v${ChannelMessageCodec.PROTOCOL_VERSION}); actualice ambas apps"
                )
                link.close()
            } else {
                toPhoneEvent(message)?.let { send(it) }
            }
        }
        heartbeats.cancel()
        session.closeChannel()
        send(PhoneEvent.LinkClosed(closeReason.get()))
    }

    /**
     * Traduce un mensaje de protocolo en el evento de dominio equivalente; `null` para mensajes que el escritorio no consume.
     */
    private fun toPhoneEvent(message: ChannelMessage): PhoneEvent? = when (message) {
        is ChannelMessage.NotificationPosted -> PhoneEvent.IncomingNotification(
            PhoneNotificationItem(
                notificationId = message.notificationId,
                appName = message.appName,
                packageName = message.packageName,
                senderTitle = message.senderTitle,
                messageBody = message.messageBody,
                timestampLabel = TIME_LABEL.format(Instant.ofEpochMilli(clock())),
                supportsInlineReply = message.supportsInlineReply
            )
        )
        is ChannelMessage.ClipboardText ->
            if (message.origin == ContentOrigin.MOBILE) PhoneEvent.ClipboardFromPhone(message.text) else null
        is ChannelMessage.Heartbeat -> {
            val roundTrip = clock() - message.timestampMs
            if (roundTrip in 0..heartbeatTimeoutMs) {
                PhoneEvent.LinkHeartbeat(latencyMs = (roundTrip / 2).toInt(), signalQualityPercent = USB_SIGNAL_QUALITY)
            } else {
                null
            }
        }
        else -> null
    }

    private companion object {
        const val USB_SIGNAL_QUALITY = 100
        val TIME_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
    }
}
