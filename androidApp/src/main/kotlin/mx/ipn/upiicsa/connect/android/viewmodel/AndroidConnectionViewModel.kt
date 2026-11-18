package mx.ipn.upiicsa.connect.android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.ipn.upiicsa.connect.android.model.AndroidDeviceRepository
import mx.ipn.upiicsa.connect.android.model.AndroidMediaStreamRepository
import mx.ipn.upiicsa.connect.android.model.AndroidNotificationBridgeRepository
import mx.ipn.upiicsa.connect.shared.model.ConnectionDirection
import mx.ipn.upiicsa.connect.shared.model.ConnectionStatus
import mx.ipn.upiicsa.connect.shared.model.LinkFeatureToggles
import mx.ipn.upiicsa.connect.shared.model.NotificationResolutionAction
import mx.ipn.upiicsa.connect.shared.model.PhoneNotificationItem
import mx.ipn.upiicsa.connect.shared.model.RemoteDeviceDescriptor
import mx.ipn.upiicsa.connect.shared.model.ThemePreference
import mx.ipn.upiicsa.connect.shared.model.TransportType
import mx.ipn.upiicsa.connect.shared.protocol.DummyRustCoreBridge

/**
 * Representa el estado único e inmutable consumido por la vista Compose en el patrón MVVM de Android.
 */
data class AndroidConnectionUiState(
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val direction: ConnectionDirection = ConnectionDirection.MOBILE_TO_DESKTOP,
    val preferredTransport: TransportType = TransportType.USB_CABLE,
    val themePreference: ThemePreference = ThemePreference.SYSTEM_AUTO,
    val availableDesktops: List<RemoteDeviceDescriptor> = emptyList(),
    val connectedDesktop: RemoteDeviceDescriptor? = null,
    val featureToggles: LinkFeatureToggles = LinkFeatureToggles(),
    val notifications: List<PhoneNotificationItem> = emptyList(),
    val clipboardText: String = "",
    val musicModePeakLevel: Float = 0f,
    val statusMessage: String = "Listo para enlazar con ordenador Windows o GNU/Linux"
)

/**
 * ViewModel MVVM que coordina repositorios Android sin contener referencias visuales a Activity o Composables.
 */
class AndroidConnectionViewModel(
    private val deviceRepository: AndroidDeviceRepository,
    private val notificationRepository: AndroidNotificationBridgeRepository,
    private val mediaStreamRepository: AndroidMediaStreamRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AndroidConnectionUiState())
    val uiState: StateFlow<AndroidConnectionUiState> = _uiState.asStateFlow()

    init {
        cargarEstadoInicial()
    }

    /**
     * Carga los equipos disponibles y las notificaciones activas al iniciar la pantalla móvil.
     */
    fun cargarEstadoInicial() {
        viewModelScope.launch {
            val desktops = deviceRepository.scanAvailableDesktops()
            val notifs = notificationRepository.loadActiveNotifications()
            _uiState.update { state ->
                state.copy(
                    availableDesktops = desktops,
                    notifications = notifs
                )
            }
        }
    }

    /**
     * Cambia la dirección de emparejamiento entre Móvil -> Ordenador y Ordenador -> Móvil.
     */
    fun setConnectionDirection(direction: ConnectionDirection) {
        _uiState.update { it.copy(direction = direction) }
    }

    /**
     * Establece la preferencia de medio de transporte manteniendo el cable USB como primera opción recomendada.
     */
    fun setPreferredTransport(transport: TransportType) {
        _uiState.update { it.copy(preferredTransport = transport) }
    }

    /**
     * Cambia la preferencia de tema claro, oscuro o automático del sistema.
     */
    fun setThemePreference(preference: ThemePreference) {
        _uiState.update { it.copy(themePreference = preference) }
    }

    /**
     * Conecta el dispositivo Android con el ordenador seleccionado usando el transporte prioritario disponible.
     */
    fun connectToDesktop(desktop: RemoteDeviceDescriptor) {
        viewModelScope.launch {
            val current = _uiState.value
            val chosen = deviceRepository.connectToDesktop(
                desktop = desktop,
                requestedTransport = current.preferredTransport,
                direction = current.direction
            )
            _uiState.update { state ->
                state.copy(
                    status = ConnectionStatus.CONNECTED,
                    connectedDesktop = desktop,
                    statusMessage = "Enlazado a ${desktop.deviceName} vía ${chosen.displayLabel}"
                )
            }
        }
    }

    /**
     * Cierra la sesión actual con el equipo de escritorio y detiene flujos activos.
     */
    fun disconnectDesktop() {
        viewModelScope.launch {
            deviceRepository.disconnectDesktop()
            _uiState.update { state ->
                state.copy(
                    status = ConnectionStatus.DISCONNECTED,
                    connectedDesktop = null,
                    musicModePeakLevel = 0f,
                    featureToggles = LinkFeatureToggles(),
                    statusMessage = "Desconectado del ordenador"
                )
            }
        }
    }

    /**
     * Activa o desactiva la sincronización automática de notificaciones hacia el ordenador.
     */
    fun toggleNotificationBridge(enabled: Boolean) {
        _uiState.update { state ->
            state.copy(
                featureToggles = state.featureToggles.copy(syncNotifications = enabled),
                statusMessage = if (enabled) "Puente de notificaciones activo" else "Puente de notificaciones pausado"
            )
        }
    }

    /**
     * Simula o procesa la resolución de una notificación móvil ordenada desde el escritorio.
     */
    fun simulateResolveFromDesktop(action: NotificationResolutionAction) {
        viewModelScope.launch {
            val updated = notificationRepository.resolveNotificationFromDesktop(action)
            _uiState.update { state ->
                state.copy(
                    notifications = updated,
                    statusMessage = "Notificación resuelta remotamente sin interrumpir flujo"
                )
            }
        }
    }

    /**
     * Sincroniza texto desde el portapapeles de Android hacia la sesión conectada.
     */
    fun syncClipboard(text: String) {
        viewModelScope.launch {
            val synced = deviceRepository.syncClipboardFromMobile(text)
            _uiState.update { state ->
                state.copy(
                    clipboardText = synced,
                    statusMessage = "Portapapeles enviado al ordenador (${synced.length} caracteres)"
                )
            }
        }
    }

    /**
     * Activa o desactiva Music Mode para reproducir el audio del teléfono en la tarjeta de sonido del PC.
     */
    fun toggleMusicMode(enabled: Boolean) {
        viewModelScope.launch {
            val updatedConfig = _uiState.value.featureToggles.musicMode.copy(
                enabled = enabled,
                activeTrackTitle = if (enabled) "Reproduciendo en tarjeta de sonido PC" else "Sin reproducción activa"
            )
            val metrics = mediaStreamRepository.configureMusicModeCapture(updatedConfig)
            _uiState.update { state ->
                state.copy(
                    featureToggles = state.featureToggles.copy(musicMode = updatedConfig),
                    musicModePeakLevel = metrics?.peakNormalized ?: 0f,
                    statusMessage = if (enabled) "Music Mode activo: audio dirigido a PC" else "Music Mode detenido"
                )
            }
        }
    }

    /**
     * Habilita o deshabilita el envío de cámara y micrófono del teléfono para videollamadas en el portátil.
     */
    fun toggleCameraAndMic(enabled: Boolean, useBackCamera: Boolean) {
        viewModelScope.launch {
            val updatedConfig = mediaStreamRepository.configureCameraMicStream(
                _uiState.value.featureToggles.remoteCameraMic.copy(
                    cameraEnabled = enabled,
                    microphoneEnabled = enabled,
                    useBackCamera = useBackCamera
                )
            )
            _uiState.update { state ->
                state.copy(
                    featureToggles = state.featureToggles.copy(remoteCameraMic = updatedConfig),
                    statusMessage = if (enabled) "Cámara y micrófono remotos activos" else "Cámara y micrófono detenidos"
                )
            }
        }
    }

    /**
     * Activa o desactiva el modo Monitor Secundario para utilizar este dispositivo Android como pantalla extendida.
     */
    fun toggleSecondaryDisplay(enabled: Boolean) {
        viewModelScope.launch {
            val updatedConfig = mediaStreamRepository.configureSecondaryDisplayReceiver(
                _uiState.value.featureToggles.secondaryDisplay.copy(enabled = enabled)
            )
            _uiState.update { state ->
                state.copy(
                    featureToggles = state.featureToggles.copy(secondaryDisplay = updatedConfig),
                    statusMessage = if (enabled) "Dispositivo listo como monitor secundario" else "Monitor secundario desactivado"
                )
            }
        }
    }

    companion object {
        /**
         * Construye una instancia por defecto del ViewModel inyectando los repositorios de la capa Model.
         */
        fun createDefault(): AndroidConnectionViewModel {
            // [DECISIÓN TÉCNICA]: Se emplea DummyRustCoreBridge en la fábrica por defecto hasta integrar la librería .so Android NDK.
            val rustCore = DummyRustCoreBridge()
            return AndroidConnectionViewModel(
                deviceRepository = AndroidDeviceRepository(rustCore),
                notificationRepository = AndroidNotificationBridgeRepository(),
                mediaStreamRepository = AndroidMediaStreamRepository(rustCore)
            )
        }
    }
}
