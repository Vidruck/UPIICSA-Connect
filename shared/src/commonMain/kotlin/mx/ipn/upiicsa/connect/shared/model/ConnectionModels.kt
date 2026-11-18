package mx.ipn.upiicsa.connect.shared.model

/**
 * Define los medios de transporte disponibles respetando la prioridad oficial del proyecto:
 * cable USB como primera opción y enlaces inalámbricos (Wi-Fi/Bluetooth) como segunda opción.
 */
enum class TransportType(val priorityRank: Int, val displayLabel: String) {
    USB_CABLE(priorityRank = 1, displayLabel = "Cable USB (Prioridad 1)"),
    WIFI_LAN(priorityRank = 2, displayLabel = "Wi-Fi LAN (Prioridad 2)"),
    BLUETOOTH(priorityRank = 2, displayLabel = "Bluetooth RFCOMM (Prioridad 2)")
}

/**
 * Indica la dirección desde la cual se inició el emparejamiento o solicitud de enlace.
 */
enum class ConnectionDirection(val displayLabel: String) {
    DESKTOP_TO_MOBILE("Ordenador -> Móvil"),
    MOBILE_TO_DESKTOP("Móvil -> Ordenador")
}

/**
 * Identifica los sistemas operativos de escritorio soportados de forma nativa.
 */
enum class DesktopOsPlatform {
    GNU_LINUX,
    WINDOWS
}

/**
 * Representa el estado actual del ciclo de vida de la conexión entre dispositivos.
 */
enum class ConnectionStatus {
    DISCONNECTED,
    SEARCHING,
    PAIRING,
    CONNECTED,
    ERROR
}

/**
 * Agrupa las preferencias de tema visual adaptable al sistema operativo.
 */
enum class ThemePreference(val displayLabel: String) {
    SYSTEM_AUTO("Automático del sistema"),
    LIGHT("Modo Claro UPIICSA"),
    DARK("Modo Oscuro UPIICSA")
}

/**
 * Describe un dispositivo remoto descubierto para establecer sesión.
 */
data class RemoteDeviceDescriptor(
    val deviceId: String,
    val deviceName: String,
    val modelName: String,
    val androidSdkVersion: Int?,
    val availableTransports: List<TransportType>,
    val preferredTransport: TransportType
)

/**
 * Representa una notificación proveniente del teléfono Android susceptible de resolverse desde el ordenador.
 */
data class PhoneNotificationItem(
    val notificationId: String,
    val appName: String,
    val packageName: String,
    val senderTitle: String,
    val messageBody: String,
    val timestampLabel: String,
    val supportsInlineReply: Boolean,
    val isResolved: Boolean = false,
    val resolutionSummary: String? = null
)

/**
 * Modela las acciones disponibles para resolver una notificación móvil directamente desde el escritorio.
 */
sealed class NotificationResolutionAction {
    data class QuickReply(val notificationId: String, val replyText: String) : NotificationResolutionAction()
    data class MarkAsRead(val notificationId: String) : NotificationResolutionAction()
    data class Dismiss(val notificationId: String) : NotificationResolutionAction()
}

/**
 * Configura el perfil de transmisión de pantalla cuando un dispositivo actúa como monitor secundario.
 */
data class SecondaryDisplayConfig(
    val enabled: Boolean = false,
    val widthPx: Int = 1920,
    val heightPx: Int = 1080,
    val targetFps: Int = 60,
    val touchInputReturnEnabled: Boolean = true
)

/**
 * Configura la captura de cámara y micrófono del teléfono como periféricos virtuales para videollamadas en la PC.
 */
data class RemoteCameraMicConfig(
    val cameraEnabled: Boolean = false,
    val microphoneEnabled: Boolean = false,
    val useBackCamera: Boolean = false,
    val resolutionLabel: String = "1080p @ 30fps"
)

/**
 * Configura el modo Music Mode para enviar audio del teléfono a la tarjeta de sonido de la PC.
 */
data class MusicModeStreamConfig(
    val enabled: Boolean = false,
    val sampleRateHz: Int = 48_000,
    val gainBoostFactor: Float = 1.15f,
    val activeTrackTitle: String = "Sin reproducción activa"
)

/**
 * Resume la configuración activa de todos los módulos funcionales en el panel de opciones.
 */
data class LinkFeatureToggles(
    val syncNotifications: Boolean = true,
    val syncClipboard: Boolean = true,
    val secondaryDisplay: SecondaryDisplayConfig = SecondaryDisplayConfig(),
    val remoteCameraMic: RemoteCameraMicConfig = RemoteCameraMicConfig(),
    val musicMode: MusicModeStreamConfig = MusicModeStreamConfig()
)
