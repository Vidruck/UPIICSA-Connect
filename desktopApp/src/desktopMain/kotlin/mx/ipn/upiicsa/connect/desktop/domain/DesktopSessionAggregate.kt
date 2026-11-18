package mx.ipn.upiicsa.connect.desktop.domain

import mx.ipn.upiicsa.connect.shared.model.ConnectionDirection
import mx.ipn.upiicsa.connect.shared.model.ConnectionStatus
import mx.ipn.upiicsa.connect.shared.model.LinkFeatureToggles
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
    val featureToggles: LinkFeatureToggles = LinkFeatureToggles(),
    val notifications: List<PhoneNotificationItem> = emptyList(),
    val sharedClipboardText: String = "",
    val statusMessage: String = "Listo para enlazar dispositivo Android"
)

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
     * Registra la transición a estado de conexión establecida validando que el medio elegido respete la prioridad oficial.
     */
    fun establishConnection(
        device: RemoteDeviceDescriptor,
        requestedTransport: TransportType?,
        direction: ConnectionDirection
    ): DesktopSessionState {
        val selectedTransport = selectEffectiveTransport(device.availableTransports, requestedTransport)
        currentState = currentState.copy(
            status = ConnectionStatus.CONNECTED,
            direction = direction,
            activeDevice = device,
            activeTransport = selectedTransport,
            statusMessage = "Conectado con ${device.deviceName} vía ${selectedTransport.displayLabel}"
        )
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
            featureToggles = currentState.featureToggles.copy(
                secondaryDisplay = currentState.featureToggles.secondaryDisplay.copy(enabled = false),
                remoteCameraMic = currentState.featureToggles.remoteCameraMic.copy(
                    cameraEnabled = false,
                    microphoneEnabled = false
                ),
                musicMode = currentState.featureToggles.musicMode.copy(enabled = false)
            ),
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
     * Registra el último contenido sincronizado en el portapapeles compartido.
     */
    fun updateClipboard(text: String): DesktopSessionState {
        currentState = currentState.copy(
            sharedClipboardText = text,
            statusMessage = "Portapapeles sincronizado (${text.length} caracteres)"
        )
        return currentState
    }

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
}
