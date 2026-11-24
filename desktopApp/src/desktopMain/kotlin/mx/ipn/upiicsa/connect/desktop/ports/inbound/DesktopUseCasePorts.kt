package mx.ipn.upiicsa.connect.desktop.ports.inbound

import kotlinx.coroutines.flow.StateFlow
import mx.ipn.upiicsa.connect.desktop.domain.DesktopSessionState
import mx.ipn.upiicsa.connect.desktop.domain.PhoneEvent
import mx.ipn.upiicsa.connect.shared.model.ConnectionDirection
import mx.ipn.upiicsa.connect.shared.model.NotificationResolutionAction
import mx.ipn.upiicsa.connect.shared.model.PhoneNotificationItem
import mx.ipn.upiicsa.connect.shared.model.RemoteDeviceDescriptor
import mx.ipn.upiicsa.connect.shared.model.SecondaryDisplayConfig
import mx.ipn.upiicsa.connect.shared.model.TransportType

/**
 * Puerto de entrada para descubrir, enlazar y desconectar dispositivos Android desde el ordenador o aceptar conexiones entrantes.
 */
interface ConnectDeviceUseCase {

    /**
     * Explora dispositivos disponibles priorizando cable USB y secundariamente Wi-Fi/Bluetooth.
     */
    suspend fun discoverAvailableDevices(): List<RemoteDeviceDescriptor>

    /**
     * Establece una sesión activa con el dispositivo seleccionado en la dirección y medio especificados.
     */
    suspend fun connectToDevice(
        device: RemoteDeviceDescriptor,
        preferredTransport: TransportType?,
        direction: ConnectionDirection
    ): DesktopSessionState

    /**
     * Finaliza la conexión activa con el dispositivo remoto.
     */
    suspend fun disconnect(): DesktopSessionState
}

/**
 * Puerto de entrada dedicado exclusivamente al espejo y resolución remota de notificaciones móviles en la PC.
 */
interface NotificationResolutionUseCase {

    /**
     * Carga o actualiza el lote de notificaciones provenientes del teléfono.
     */
    suspend fun syncIncomingNotifications(items: List<PhoneNotificationItem>): DesktopSessionState

    /**
     * Resuelve una notificación del teléfono directamente desde el ordenador sin cambiar de dispositivo.
     */
    suspend fun resolveNotification(action: NotificationResolutionAction): DesktopSessionState

    /**
     * Activa o pausa el espejo automático de notificaciones del teléfono.
     */
    suspend fun setNotificationMirroring(enabled: Boolean): DesktopSessionState
}

/**
 * Puerto de entrada para la sincronización bidireccional del portapapeles entre escritorio y Android.
 */
interface ClipboardSyncUseCase {

    /**
     * Envía texto del portapapeles local hacia el dispositivo conectado o aplica texto recibido desde Android.
     */
    suspend fun pushClipboardText(text: String): DesktopSessionState

    /**
     * Lee el contenido actual del portapapeles nativo del sistema operativo de escritorio.
     */
    suspend fun pullOsClipboardText(): String

    /**
     * Activa o pausa la sincronización automática del portapapeles.
     */
    suspend fun setClipboardSync(enabled: Boolean): DesktopSessionState
}

/**
 * Puerto de entrada para habilitar o ajustar el dispositivo Android/tablet como monitor secundario de la PC.
 */
interface SecondaryMonitorUseCase {

    /**
     * Activa o desactiva la extensión de pantalla virtual hacia el dispositivo Android conectado.
     */
    suspend fun toggleSecondaryDisplay(config: SecondaryDisplayConfig): DesktopSessionState
}

/**
 * Puerto de entrada para usar cámara y micrófono del teléfono como dispositivos virtuales para videollamadas en el portátil.
 */
interface VirtualCameraMicUseCase {

    /**
     * Habilita o deshabilita el puente de cámara y micrófono remoto por USB o inalámbrico.
     */
    suspend fun toggleVirtualCameraAndMic(
        enableCamera: Boolean,
        enableMicrophone: Boolean,
        useBackCamera: Boolean
    ): DesktopSessionState
}

/**
 * Puerto de entrada para activar Music Mode y reproducir el audio del teléfono en la tarjeta de sonido de la PC.
 */
interface MusicModeUseCase {

    /**
     * Inicia o detiene el flujo Music Mode descargando la reproducción multimedia al teléfono.
     */
    suspend fun toggleMusicMode(
        enabled: Boolean,
        gainBoostFactor: Float,
        trackTitle: String
    ): DesktopSessionState
}

/**
 * Información del núcleo nativo en uso (versión, si está respaldado por el binario Rust o por la simulación Kotlin) y modo de enlace con el teléfono.
 */
data class CoreRuntimeInfo(
    val version: String,
    val nativeBacked: Boolean,
    val linkModeLabel: String
)

/**
 * Puerto de entrada para observar la sesión y procesar los eventos entrantes del teléfono conectado.
 */
interface SessionObservationUseCase {

    /**
     * Flujo observable del estado de la sesión para la capa de presentación.
     */
    val sessionState: StateFlow<DesktopSessionState>

    /**
     * Describe el núcleo nativo activo.
     */
    fun coreRuntimeInfo(): CoreRuntimeInfo

    /**
     * Aplica un evento recibido del teléfono a la sesión y a los adaptadores del sistema operativo.
     */
    suspend fun processPhoneEvent(event: PhoneEvent): DesktopSessionState

    /**
     * Escucha de forma continua los eventos del teléfono mientras haya una sesión activa (suspende indefinidamente).
     */
    suspend fun runPhoneEventLoop()
}

/**
 * Fachada que agrupa todos los puertos de entrada consumidos por el adaptador conductor (interfaz gráfica).
 */
interface DesktopUseCases :
    ConnectDeviceUseCase,
    NotificationResolutionUseCase,
    ClipboardSyncUseCase,
    SecondaryMonitorUseCase,
    VirtualCameraMicUseCase,
    MusicModeUseCase,
    SessionObservationUseCase
