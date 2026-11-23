package mx.ipn.upiicsa.connect.desktop.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.ipn.upiicsa.connect.desktop.domain.DesktopSessionState
import mx.ipn.upiicsa.connect.desktop.ports.inbound.CoreRuntimeInfo
import mx.ipn.upiicsa.connect.desktop.ports.inbound.DesktopUseCases
import mx.ipn.upiicsa.connect.shared.model.ConnectionDirection
import mx.ipn.upiicsa.connect.shared.model.NotificationResolutionAction
import mx.ipn.upiicsa.connect.shared.model.RemoteDeviceDescriptor
import mx.ipn.upiicsa.connect.shared.model.SecondaryDisplayConfig
import mx.ipn.upiicsa.connect.shared.model.ThemePreference
import mx.ipn.upiicsa.connect.shared.model.TransportType

/**
 * Secciones navegables del panel de escritorio.
 */
enum class DesktopSection(val label: String, val title: String, val icon: ImageVector) {
    INICIO("Inicio", "Resumen de la sesión", Icons.Rounded.Dashboard),
    DISPOSITIVOS("Dispositivos", "Conexión y transporte", Icons.Rounded.Devices),
    NOTIFICACIONES("Avisos", "Notificaciones del teléfono", Icons.Rounded.Notifications),
    PORTAPAPELES("Portapapeles", "Portapapeles compartido", Icons.Rounded.ContentPaste),
    MULTIMEDIA("Multimedia", "Music Mode, cámara y micrófono", Icons.Rounded.GraphicEq),
    MONITOR("Monitor", "Monitor secundario", Icons.Rounded.DesktopWindows),
    AJUSTES("Ajustes", "Ajustes y acerca de", Icons.Rounded.Settings)
}

/**
 * Estado local de presentación (navegación, borradores y preferencias) que no pertenece al dominio.
 */
data class DesktopPanelLocalState(
    val section: DesktopSection = DesktopSection.INICIO,
    val themePreference: ThemePreference = ThemePreference.SYSTEM_AUTO,
    val devices: List<RemoteDeviceDescriptor> = emptyList(),
    val isDiscovering: Boolean = false,
    val preferredTransport: TransportType = TransportType.USB_CABLE,
    val direction: ConnectionDirection = ConnectionDirection.DESKTOP_TO_MOBILE,
    val clipboardDraft: String = "",
    val musicGain: Float = 1.15f,
    val displayDraft: SecondaryDisplayConfig = SecondaryDisplayConfig()
)

/**
 * Controlador de presentación del adaptador conductor: traduce gestos de la interfaz en llamadas a los puertos de entrada.
 */
class DesktopPanelController(
    private val useCases: DesktopUseCases,
    private val scope: CoroutineScope,
    val platformLabel: String
) {
    private val mutableLocal = MutableStateFlow(
        DesktopPanelLocalState(
            musicGain = useCases.sessionState.value.featureToggles.musicMode.gainBoostFactor,
            displayDraft = useCases.sessionState.value.featureToggles.secondaryDisplay
        )
    )

    /**
     * Estado de la sesión de dominio expuesto por el servicio de aplicación.
     */
    val session: StateFlow<DesktopSessionState> = useCases.sessionState

    /**
     * Estado local de la interfaz.
     */
    val local: StateFlow<DesktopPanelLocalState> = mutableLocal.asStateFlow()

    /**
     * Información del núcleo nativo activo.
     */
    val coreInfo: CoreRuntimeInfo = useCases.coreRuntimeInfo()

    /**
     * Inicia la escucha de eventos del teléfono y la primera búsqueda de dispositivos.
     */
    fun start() {
        scope.launch { useCases.runPhoneEventLoop() }
        refreshDevices()
    }

    /**
     * Cambia la sección visible del panel.
     */
    fun selectSection(section: DesktopSection) = mutableLocal.update { it.copy(section = section) }

    /**
     * Cambia la preferencia de tema claro, oscuro o automático.
     */
    fun setThemePreference(preference: ThemePreference) = mutableLocal.update { it.copy(themePreference = preference) }

    /**
     * Cambia el medio de transporte preferido para la siguiente conexión.
     */
    fun setPreferredTransport(transport: TransportType) = mutableLocal.update { it.copy(preferredTransport = transport) }

    /**
     * Cambia la dirección de enlace (ordenador -> móvil o móvil -> ordenador).
     */
    fun setDirection(direction: ConnectionDirection) = mutableLocal.update { it.copy(direction = direction) }

    /**
     * Actualiza el borrador del portapapeles compartido.
     */
    fun updateClipboardDraft(text: String) = mutableLocal.update { it.copy(clipboardDraft = text) }

    /**
     * Vuelve a explorar dispositivos disponibles.
     */
    fun refreshDevices() {
        scope.launch {
            mutableLocal.update { it.copy(isDiscovering = true) }
            val devices = useCases.discoverAvailableDevices()
            mutableLocal.update { it.copy(devices = devices, isDiscovering = false) }
        }
    }

    /**
     * Conecta con el dispositivo usando el transporte y la dirección seleccionados.
     */
    fun connect(device: RemoteDeviceDescriptor) {
        val current = mutableLocal.value
        scope.launch { useCases.connectToDevice(device, current.preferredTransport, current.direction) }
    }

    /**
     * Cierra la sesión activa.
     */
    fun disconnect() {
        scope.launch { useCases.disconnect() }
    }

    /**
     * Resuelve una notificación del teléfono desde el ordenador.
     */
    fun resolveNotification(action: NotificationResolutionAction) {
        scope.launch { useCases.resolveNotification(action) }
    }

    /**
     * Activa o pausa el espejo de notificaciones.
     */
    fun setNotificationMirroring(enabled: Boolean) {
        scope.launch { useCases.setNotificationMirroring(enabled) }
    }

    /**
     * Activa o pausa el portapapeles compartido.
     */
    fun setClipboardSync(enabled: Boolean) {
        scope.launch { useCases.setClipboardSync(enabled) }
    }

    /**
     * Envía el borrador actual al teléfono y lo limpia.
     */
    fun sendClipboardDraft() {
        val text = mutableLocal.value.clipboardDraft
        scope.launch {
            useCases.pushClipboardText(text)
            mutableLocal.update { it.copy(clipboardDraft = "") }
        }
    }

    /**
     * Copia al borrador el contenido actual del portapapeles del sistema operativo.
     */
    fun loadOsClipboardIntoDraft() {
        scope.launch {
            val text = useCases.pullOsClipboardText()
            mutableLocal.update { it.copy(clipboardDraft = text) }
        }
    }

    /**
     * Activa o desactiva el monitor secundario con la configuración del borrador.
     */
    fun setSecondaryDisplayEnabled(enabled: Boolean) {
        val config = mutableLocal.value.displayDraft.copy(enabled = enabled)
        mutableLocal.update { it.copy(displayDraft = config) }
        scope.launch { useCases.toggleSecondaryDisplay(config) }
    }

    /**
     * Ajusta resolución, tasa o retorno táctil; si el monitor está activo la aplica de inmediato.
     */
    fun updateDisplayConfig(transform: (SecondaryDisplayConfig) -> SecondaryDisplayConfig) {
        val enabled = session.value.featureToggles.secondaryDisplay.enabled
        val config = transform(mutableLocal.value.displayDraft).copy(enabled = enabled)
        mutableLocal.update { it.copy(displayDraft = config) }
        if (enabled) {
            scope.launch { useCases.toggleSecondaryDisplay(config) }
        }
    }

    /**
     * Activa o desactiva la cámara remota conservando el estado del micrófono.
     */
    fun setCameraEnabled(enabled: Boolean) {
        val current = session.value.featureToggles.remoteCameraMic
        scope.launch { useCases.toggleVirtualCameraAndMic(enabled, current.microphoneEnabled, current.useBackCamera) }
    }

    /**
     * Activa o desactiva el micrófono remoto conservando el estado de la cámara.
     */
    fun setMicrophoneEnabled(enabled: Boolean) {
        val current = session.value.featureToggles.remoteCameraMic
        scope.launch { useCases.toggleVirtualCameraAndMic(current.cameraEnabled, enabled, current.useBackCamera) }
    }

    /**
     * Alterna entre cámara frontal y trasera del teléfono.
     */
    fun setUseBackCamera(useBack: Boolean) {
        val current = session.value.featureToggles.remoteCameraMic
        scope.launch { useCases.toggleVirtualCameraAndMic(current.cameraEnabled, current.microphoneEnabled, useBack) }
    }

    /**
     * Activa o detiene Music Mode con la ganancia seleccionada.
     */
    fun setMusicModeEnabled(enabled: Boolean) {
        val gain = mutableLocal.value.musicGain
        val title = session.value.featureToggles.musicMode.activeTrackTitle
        scope.launch {
            useCases.toggleMusicMode(enabled, gain, if (enabled) title else "Sin reproducción activa")
        }
    }

    /**
     * Actualiza el borrador de ganancia mientras se arrastra el control deslizante.
     */
    fun updateMusicGainDraft(gain: Float) = mutableLocal.update { it.copy(musicGain = gain) }

    /**
     * Aplica la ganancia seleccionada si Music Mode está activo.
     */
    fun commitMusicGain() {
        val music = session.value.featureToggles.musicMode
        if (music.enabled) {
            val gain = mutableLocal.value.musicGain
            scope.launch { useCases.toggleMusicMode(true, gain, music.activeTrackTitle) }
        }
    }
}
