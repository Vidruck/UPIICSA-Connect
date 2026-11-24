package mx.ipn.upiicsa.connect.desktop.domain

import mx.ipn.upiicsa.connect.shared.model.ActivityCategory
import mx.ipn.upiicsa.connect.shared.model.ActivityLogEntry
import mx.ipn.upiicsa.connect.shared.model.ClipboardHistoryEntry
import mx.ipn.upiicsa.connect.shared.model.ConnectionDirection
import mx.ipn.upiicsa.connect.shared.model.ConnectionStatus
import mx.ipn.upiicsa.connect.shared.model.LinkFeatureToggles
import mx.ipn.upiicsa.connect.shared.model.LinkTelemetry
import mx.ipn.upiicsa.connect.shared.model.MediaTelemetry
import mx.ipn.upiicsa.connect.shared.model.PhoneNotificationItem
import mx.ipn.upiicsa.connect.shared.model.RemoteDeviceDescriptor
import mx.ipn.upiicsa.connect.shared.model.TransportType

/**
 * Representa el estado inmutable de la sesión de escritorio en la capa de dominio puro.
 */
data class DesktopSessionState(
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val direction: ConnectionDirection = ConnectionDirection.DESKTOP_TO_MOBILE,
    val activeDevice: RemoteDeviceDescriptor? = null,
    val activeTransport: TransportType? = null,
    val pairingPin: Int? = null,
    val featureToggles: LinkFeatureToggles = LinkFeatureToggles(),
    val notifications: List<PhoneNotificationItem> = emptyList(),
    val sharedClipboardText: String = "",
    val clipboardHistory: List<ClipboardHistoryEntry> = emptyList(),
    val linkTelemetry: LinkTelemetry = LinkTelemetry(),
    val mediaTelemetry: MediaTelemetry = MediaTelemetry(),
    val frameTick: Long = 0L,
    val activityLog: List<ActivityLogEntry> = emptyList(),
    val statusMessage: String = "Listo para enlazar dispositivo Android"
) {
    /**
     * Cantidad de notificaciones del teléfono pendientes de resolver.
     */
    val pendingNotificationCount: Int
        get() = notifications.count { !it.isResolved }
}

/**
 * Agregado de dominio puro que aplica invariantes de prioridad de transporte y transición de estado en escritorio.
 */
class DesktopSessionAggregate(
    initialState: DesktopSessionState = DesktopSessionState()
) {
    private var currentState: DesktopSessionState = initialState

    /**
     * Obtiene una instantánea inmutable del estado actual de la sesión.
     */
    fun snapshot(): DesktopSessionState = currentState

    /**
     * Marca la sesión en búsqueda de dispositivos mientras el transporte explora USB, LAN y Bluetooth.
     */
    fun beginDiscovery(): DesktopSessionState {
        if (currentState.status == ConnectionStatus.CONNECTED) {
            return currentState
        }
        currentState = currentState.copy(
            status = ConnectionStatus.SEARCHING,
            statusMessage = "Buscando dispositivos por USB, Wi-Fi y Bluetooth…"
        )
        return currentState
    }

    /**
     * Cierra la fase de búsqueda reportando la cantidad de dispositivos encontrados.
     */
    fun finishDiscovery(foundCount: Int): DesktopSessionState {
        if (currentState.status != ConnectionStatus.SEARCHING) {
            return currentState
        }
        currentState = currentState.copy(
            status = ConnectionStatus.DISCONNECTED,
            statusMessage = "$foundCount dispositivo(s) disponible(s) para enlazar"
        )
        return currentState
    }

    /**
     * Registra la transición a estado de conexión establecida validando que el medio elegido respete la prioridad oficial.
     */
    fun establishConnection(
        device: RemoteDeviceDescriptor,
        requestedTransport: TransportType?,
        direction: ConnectionDirection,
        pairingPin: Int? = null
    ): DesktopSessionState {
        val selectedTransport = selectEffectiveTransport(device.availableTransports, requestedTransport)
        currentState = currentState.copy(
            status = ConnectionStatus.CONNECTED,
            direction = direction,
            activeDevice = device,
            activeTransport = selectedTransport,
            pairingPin = pairingPin,
            linkTelemetry = LinkTelemetry(),
            mediaTelemetry = MediaTelemetry(),
            statusMessage = "Conectado con ${device.deviceName} vía ${selectedTransport.displayLabel}"
        )
        return currentState
    }

    /**
     * Registra una falla de enlace dejando la sesión en estado de error con un mensaje explicativo.
     */
    fun reportFailure(message: String): DesktopSessionState {
        currentState = currentState.copy(status = ConnectionStatus.ERROR, statusMessage = message)
        return currentState
    }

    /**
     * Cierra la sesión activa y desactiva módulos de transmisión en tiempo real para liberar periféricos virtuales.
     */
    fun terminateConnection(): DesktopSessionState {
        currentState = currentState.copy(
            status = ConnectionStatus.DISCONNECTED,
            activeDevice = null,
            activeTransport = null,
            pairingPin = null,
            featureToggles = currentState.featureToggles.copy(
                secondaryDisplay = currentState.featureToggles.secondaryDisplay.copy(enabled = false),
                remoteCameraMic = currentState.featureToggles.remoteCameraMic.copy(
                    cameraEnabled = false,
                    microphoneEnabled = false
                ),
                musicMode = currentState.featureToggles.musicMode.copy(
                    enabled = false,
                    activeTrackTitle = "Sin reproducción activa"
                )
            ),
            linkTelemetry = LinkTelemetry(),
            mediaTelemetry = MediaTelemetry(),
            statusMessage = "Sesión finalizada"
        )
        return currentState
    }

    /**
     * Actualiza la bandera de capacidades habilitadas en la sesión manteniendo la consistencia del agregado.
     */
    fun updateFeatures(transform: (LinkFeatureToggles) -> LinkFeatureToggles): DesktopSessionState {
        currentState = currentState.copy(
            featureToggles = transform(currentState.featureToggles)
        )
        return currentState
    }

    /**
     * Reemplaza o actualiza el listado de notificaciones móviles espejadas en la sesión.
     */
    fun upsertNotifications(items: List<PhoneNotificationItem>): DesktopSessionState {
        currentState = currentState.copy(notifications = items)
        return currentState
    }

    /**
     * Agrega una notificación entrante al inicio de la bandeja evitando duplicados por identificador.
     */
    fun addIncomingNotification(item: PhoneNotificationItem): DesktopSessionState {
        val withoutDuplicate = currentState.notifications.filterNot { it.notificationId == item.notificationId }
        currentState = currentState.copy(
            notifications = (listOf(item) + withoutDuplicate).take(MAX_NOTIFICATIONS),
            statusMessage = "Nueva notificación de ${item.appName}"
        )
        return currentState
    }

    /**
     * Marca una notificación como resuelta desde el escritorio para conservar el flujo de trabajo sin distracciones.
     */
    fun markNotificationResolved(notificationId: String, summary: String): DesktopSessionState {
        val updatedList = currentState.notifications.map { item ->
            if (item.notificationId == notificationId) {
                item.copy(isResolved = true, resolutionSummary = summary)
            } else {
                item
            }
        }
        currentState = currentState.copy(
            notifications = updatedList,
            statusMessage = "Notificación resuelta desde PC: $summary"
        )
        return currentState
    }

    /**
     * Registra el último contenido sincronizado en el portapapeles compartido y lo agrega al historial.
     */
    fun updateClipboard(text: String, fromMobile: Boolean = false, epochMillis: Long = 0L): DesktopSessionState {
        val entry = ClipboardHistoryEntry(text = text, fromMobile = fromMobile, epochMillis = epochMillis)
        currentState = currentState.copy(
            sharedClipboardText = text,
            clipboardHistory = (listOf(entry) + currentState.clipboardHistory).take(MAX_CLIPBOARD_HISTORY),
            statusMessage = if (fromMobile) {
                "Portapapeles recibido del teléfono (${text.length} caracteres)"
            } else {
                "Portapapeles sincronizado (${text.length} caracteres)"
            }
        )
        return currentState
    }

    /**
     * Actualiza la telemetría del enlace a partir de una transformación pura.
     */
    fun updateLinkTelemetry(transform: (LinkTelemetry) -> LinkTelemetry): DesktopSessionState {
        currentState = currentState.copy(linkTelemetry = transform(currentState.linkTelemetry))
        return currentState
    }

    /**
     * Actualiza la telemetría multimedia y avanza el contador de cuadros usado por las vistas previas.
     */
    fun updateMediaTelemetry(transform: (MediaTelemetry) -> MediaTelemetry): DesktopSessionState {
        currentState = currentState.copy(
            mediaTelemetry = transform(currentState.mediaTelemetry),
            frameTick = currentState.frameTick + 1
        )
        return currentState
    }

    /**
     * Agrega una entrada a la bitácora de actividad conservando solo las más recientes.
     */
    fun appendActivity(entry: ActivityLogEntry): DesktopSessionState {
        currentState = currentState.copy(
            activityLog = (currentState.activityLog + entry).takeLast(MAX_ACTIVITY_ENTRIES)
        )
        return currentState
    }

    /**
     * Construye y agrega una entrada de bitácora con la marca de tiempo indicada.
     */
    fun log(epochMillis: Long, category: ActivityCategory, message: String): DesktopSessionState =
        appendActivity(ActivityLogEntry(epochMillis, category, message))

    /**
     * Respeta el medio solicitado si el dispositivo lo ofrece; en otro caso aplica la prioridad USB -> Wi-Fi -> Bluetooth.
     */
    private fun selectEffectiveTransport(
        available: List<TransportType>,
        requested: TransportType?
    ): TransportType {
        if (requested != null && available.contains(requested)) {
            return requested
        }
        return when {
            available.contains(TransportType.USB_CABLE) -> TransportType.USB_CABLE
            available.contains(TransportType.WIFI_LAN) -> TransportType.WIFI_LAN
            available.contains(TransportType.BLUETOOTH) -> TransportType.BLUETOOTH
            else -> TransportType.USB_CABLE
        }
    }

    private companion object {
        const val MAX_NOTIFICATIONS = 30
        const val MAX_CLIPBOARD_HISTORY = 8
        const val MAX_ACTIVITY_ENTRIES = 60
    }
}
