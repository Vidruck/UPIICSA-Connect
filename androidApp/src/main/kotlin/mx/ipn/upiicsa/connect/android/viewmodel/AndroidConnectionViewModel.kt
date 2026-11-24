package mx.ipn.upiicsa.connect.android.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.ipn.upiicsa.connect.android.model.AndroidDeviceRepository
import mx.ipn.upiicsa.connect.android.model.AndroidMediaStreamRepository
import mx.ipn.upiicsa.connect.android.model.AndroidNotificationBridgeRepository
import mx.ipn.upiicsa.connect.android.model.AndroidPairingRepository
import mx.ipn.upiicsa.connect.android.model.AndroidSocketLinkRepository
import mx.ipn.upiicsa.connect.android.model.AndroidSystemClipboardRepository
import mx.ipn.upiicsa.connect.android.model.AndroidSystemNotificationRepository
import mx.ipn.upiicsa.connect.android.model.NotificationBridgeSource
import mx.ipn.upiicsa.connect.android.model.DesktopLinkEvent
import mx.ipn.upiicsa.connect.android.model.nativecore.AndroidNativeCoreLoader
import mx.ipn.upiicsa.connect.shared.model.ActivityCategory
import mx.ipn.upiicsa.connect.shared.model.ActivityLogEntry
import mx.ipn.upiicsa.connect.shared.model.ClipboardHistoryEntry
import mx.ipn.upiicsa.connect.shared.model.ConnectionDirection
import mx.ipn.upiicsa.connect.shared.model.ConnectionStatus
import mx.ipn.upiicsa.connect.shared.model.LinkFeatureToggles
import mx.ipn.upiicsa.connect.shared.model.LinkTelemetry
import mx.ipn.upiicsa.connect.shared.model.NotificationResolutionAction
import mx.ipn.upiicsa.connect.shared.model.PhoneNotificationItem
import mx.ipn.upiicsa.connect.shared.model.RemoteDeviceDescriptor
import mx.ipn.upiicsa.connect.shared.model.ThemePreference
import mx.ipn.upiicsa.connect.shared.model.TransportType

/**
 * Pestañas de navegación de la app móvil (independientes de iconos o recursos de UI).
 */
enum class AndroidTab(val label: String) {
    ENLACE("Enlace"),
    AVISOS("Avisos"),
    PORTAPAPELES("Portapapeles"),
    MULTIMEDIA("Multimedia"),
    PANTALLA("Pantalla")
}

/**
 * Representa el estado único e inmutable consumido por la vista Compose en el patrón MVVM de Android.
 */
data class AndroidConnectionUiState(
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val direction: ConnectionDirection = ConnectionDirection.MOBILE_TO_DESKTOP,
    val preferredTransport: TransportType = TransportType.USB_CABLE,
    val themePreference: ThemePreference = ThemePreference.SYSTEM_AUTO,
    val selectedTab: AndroidTab = AndroidTab.ENLACE,
    val availableDesktops: List<RemoteDeviceDescriptor> = emptyList(),
    val isScanning: Boolean = false,
    val connectedDesktop: RemoteDeviceDescriptor? = null,
    val activeTransport: TransportType? = null,
    val pairingPin: Int? = null,
    val featureToggles: LinkFeatureToggles = LinkFeatureToggles(),
    val notifications: List<PhoneNotificationItem> = emptyList(),
    val clipboardText: String = "",
    val clipboardHistory: List<ClipboardHistoryEntry> = emptyList(),
    val linkTelemetry: LinkTelemetry = LinkTelemetry(),
    val musicModePeakLevel: Float = 0f,
    val microphoneLevel: Float = 0f,
    val frameTick: Long = 0L,
    val activityLog: List<ActivityLogEntry> = emptyList(),
    val coreEngineLabel: String = "",
    val useRealLink: Boolean = false,
    val notificationAccessGranted: Boolean = false,
    val statusMessage: String = "Listo para enlazar con ordenador Windows o GNU/Linux"
) {
    /**
     * Cantidad de notificaciones que aún no se resuelven desde el ordenador.
     */
    val pendingNotificationCount: Int
        get() = notifications.count { !it.isResolved }
}

/**
 * ViewModel MVVM que coordina repositorios Android sin contener referencias visuales a Activity o Composables.
 */
class AndroidConnectionViewModel(
    private val deviceRepository: AndroidDeviceRepository,
    private val notificationRepository: AndroidNotificationBridgeRepository,
    private val mediaStreamRepository: AndroidMediaStreamRepository,
    private val pairingRepository: AndroidPairingRepository,
    private val socketLinkRepository: AndroidSocketLinkRepository,
    private val systemNotificationRepository: AndroidSystemNotificationRepository? = null,
    private val systemClipboardRepository: AndroidSystemClipboardRepository? = null,
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private val _uiState = MutableStateFlow(AndroidConnectionUiState(coreEngineLabel = deviceRepository.coreEngineLabel()))
    val uiState: StateFlow<AndroidConnectionUiState> = _uiState.asStateFlow()

    private var sessionJobs: List<Job> = emptyList()
    private var musicJob: Job? = null
    private var microphoneJob: Job? = null
    private var frameJob: Job? = null
    private var lastClipboardFromDesktop: String? = null

    init {
        cargarEstadoInicial()
    }

    /**
     * Carga los equipos disponibles y las notificaciones activas al iniciar la pantalla móvil.
     */
    fun cargarEstadoInicial() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, status = ConnectionStatus.SEARCHING) }
            val desktops = if (_uiState.value.useRealLink) {
                listOf(socketLinkRepository.usbDesktop)
            } else {
                deviceRepository.scanAvailableDesktops()
            }
            val notifs = notificationSource().loadActiveNotifications()
            _uiState.update { state ->
                state.copy(
                    availableDesktops = desktops,
                    notifications = notifs,
                    isScanning = false,
                    status = if (state.connectedDesktop != null) ConnectionStatus.CONNECTED else ConnectionStatus.DISCONNECTED,
                    statusMessage = if (state.connectedDesktop != null) state.statusMessage else "${desktops.size} ordenador(es) disponible(s)"
                )
            }
        }
    }

    /**
     * Actualiza si el usuario concedió el acceso a notificaciones (al volver de los ajustes de Android).
     */
    fun refreshSystemAccess() {
        val granted = systemNotificationRepository?.isAccessGranted() == true
        if (granted != _uiState.value.notificationAccessGranted) {
            _uiState.update { it.copy(notificationAccessGranted = granted) }
            if (_uiState.value.useRealLink) {
                cargarEstadoInicial()
            }
        }
    }

    /**
     * Cambia la pestaña visible.
     */
    fun selectTab(tab: AndroidTab) {
        _uiState.update { it.copy(selectedTab = tab) }
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
     * Alterna entre el ordenador simulado y el enlace real por cable USB (`adb reverse` hacia `127.0.0.1:47800`).
     */
    fun setRealLinkEnabled(enabled: Boolean) {
        if (_uiState.value.connectedDesktop != null) {
            disconnectDesktop()
        }
        _uiState.update { state ->
            state.copy(useRealLink = enabled)
                .withLog(ActivityCategory.CONNECTION, if (enabled) "Modo de enlace real por USB" else "Modo de enlace simulado")
        }
        cargarEstadoInicial()
    }

    /**
     * Conecta el dispositivo Android con el ordenador seleccionado usando el transporte prioritario disponible.
     */
    fun connectToDesktop(desktop: RemoteDeviceDescriptor) {
        if (_uiState.value.useRealLink) {
            connectRealDesktop(desktop)
            return
        }
        viewModelScope.launch {
            val current = _uiState.value
            _uiState.update { it.copy(status = ConnectionStatus.PAIRING, statusMessage = "Emparejando con ${desktop.deviceName}…") }
            val chosen = deviceRepository.connectToDesktop(
                desktop = desktop,
                requestedTransport = current.preferredTransport,
                direction = current.direction
            )
            val pin = pairingRepository.pairingPinFor(desktop.deviceId)
            _uiState.update { state ->
                state.copy(
                    status = ConnectionStatus.CONNECTED,
                    connectedDesktop = desktop,
                    activeTransport = chosen,
                    pairingPin = pin,
                    statusMessage = "Enlazado a ${desktop.deviceName} vía ${chosen.displayLabel}"
                ).withLog(ActivityCategory.CONNECTION, "Enlace con ${desktop.deviceName} vía ${chosen.shortLabel}")
            }
            startSessionStreams(chosen)
        }
    }

    /**
     * Cierra la sesión actual con el equipo de escritorio y detiene flujos activos.
     */
    fun disconnectDesktop() {
        viewModelScope.launch {
            stopAllStreams()
            socketLinkRepository.disconnect()
            deviceRepository.disconnectDesktop()
            _uiState.update { state ->
                state.copy(
                    status = ConnectionStatus.DISCONNECTED,
                    connectedDesktop = null,
                    activeTransport = null,
                    pairingPin = null,
                    musicModePeakLevel = 0f,
                    microphoneLevel = 0f,
                    linkTelemetry = LinkTelemetry(),
                    featureToggles = LinkFeatureToggles(),
                    statusMessage = "Desconectado del ordenador"
                ).withLog(ActivityCategory.CONNECTION, "Sesión finalizada")
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
            ).withLog(ActivityCategory.NOTIFICATION, if (enabled) "Puente de notificaciones activo" else "Puente pausado")
        }
    }

    /**
     * Simula o procesa la resolución de una notificación móvil ordenada desde el escritorio.
     */
    fun simulateResolveFromDesktop(action: NotificationResolutionAction) {
        viewModelScope.launch {
            val updated = notificationSource().resolveNotificationFromDesktop(action)
            _uiState.update { state ->
                state.copy(
                    notifications = updated,
                    statusMessage = "Notificación resuelta remotamente sin interrumpir flujo"
                ).withLog(ActivityCategory.NOTIFICATION, "Resolución recibida desde la PC")
            }
        }
    }

    /**
     * Activa o pausa el portapapeles compartido.
     */
    fun toggleClipboardSync(enabled: Boolean) {
        _uiState.update { state ->
            state.copy(featureToggles = state.featureToggles.copy(syncClipboard = enabled))
                .withLog(ActivityCategory.CLIPBOARD, if (enabled) "Portapapeles compartido activo" else "Portapapeles pausado")
        }
    }

    /**
     * Sincroniza texto desde el portapapeles de Android hacia la sesión conectada.
     */
    fun syncClipboard(text: String) {
        if (text.isBlank()) {
            return
        }
        viewModelScope.launch {
            val synced = deviceRepository.syncClipboardFromMobile(text)
            if (socketLinkRepository.isConnected) {
                socketLinkRepository.publishClipboard(synced)
            }
            _uiState.update { state ->
                state.copy(
                    clipboardText = synced,
                    clipboardHistory = (listOf(ClipboardHistoryEntry(synced, fromMobile = true, epochMillis = clock())) + state.clipboardHistory).take(8),
                    statusMessage = "Portapapeles enviado al ordenador (${synced.length} caracteres)"
                ).withLog(ActivityCategory.CLIPBOARD, "Texto enviado al ordenador (${synced.length} caracteres)")
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
                ).withLog(ActivityCategory.MEDIA, if (enabled) "Music Mode activo" else "Music Mode detenido")
            }
            musicJob?.cancel()
            musicJob = if (enabled) {
                viewModelScope.launch {
                    mediaStreamRepository.musicLevelStream(updatedConfig.gainBoostFactor).collect { level ->
                        _uiState.update { it.copy(musicModePeakLevel = level) }
                    }
                }
            } else {
                null
            }
            refreshFrameClock()
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
                    microphoneLevel = if (enabled) state.microphoneLevel else 0f,
                    statusMessage = if (enabled) "Cámara y micrófono remotos activos" else "Cámara y micrófono detenidos"
                ).withLog(ActivityCategory.MEDIA, if (enabled) "Cámara y micrófono activos" else "Cámara y micrófono detenidos")
            }
            microphoneJob?.cancel()
            microphoneJob = if (enabled) {
                viewModelScope.launch {
                    mediaStreamRepository.microphoneLevelStream().collect { level ->
                        _uiState.update { it.copy(microphoneLevel = level) }
                    }
                }
            } else {
                null
            }
            refreshFrameClock()
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
                ).withLog(ActivityCategory.DISPLAY, if (enabled) "Receptor de pantalla activo" else "Receptor de pantalla detenido")
            }
            refreshFrameClock()
        }
    }

    /**
     * Inicia los flujos ligados a la sesión: telemetría, portapapeles del ordenador y notificaciones nuevas.
     */
    private fun startSessionStreams(transport: TransportType) {
        sessionJobs.forEach { it.cancel() }
        sessionJobs = listOf(
            viewModelScope.launch {
                deviceRepository.linkTelemetryStream(transport).collect { telemetry ->
                    _uiState.update { it.copy(linkTelemetry = telemetry) }
                }
            },
            viewModelScope.launch {
                deviceRepository.clipboardFromDesktopStream().collect { text ->
                    _uiState.update { state ->
                        if (!state.featureToggles.syncClipboard) {
                            state
                        } else {
                            state.copy(
                                clipboardText = text,
                                clipboardHistory = (listOf(ClipboardHistoryEntry(text, fromMobile = false, epochMillis = clock())) + state.clipboardHistory).take(8)
                            ).withLog(ActivityCategory.CLIPBOARD, "Texto recibido del ordenador")
                        }
                    }
                }
            },
            viewModelScope.launch {
                notificationRepository.incomingNotifications().collect { item ->
                    _uiState.update { state ->
                        if (!state.featureToggles.syncNotifications) {
                            state
                        } else {
                            state.copy(notifications = listOf(item) + state.notifications.filterNot { it.notificationId == item.notificationId })
                                .withLog(ActivityCategory.NOTIFICATION, "${item.appName} espejada al ordenador")
                        }
                    }
                }
            }
        )
    }

    /**
     * Abre el enlace real con el ordenador por TCP y arranca los flujos de la sesión real.
     */
    private fun connectRealDesktop(desktop: RemoteDeviceDescriptor) {
        viewModelScope.launch {
            _uiState.update { it.copy(status = ConnectionStatus.PAIRING, statusMessage = "Conectando con el ordenador por USB…") }
            val error = socketLinkRepository.connect(_uiState.value.direction)
            if (error != null) {
                _uiState.update { state ->
                    state.copy(
                        status = ConnectionStatus.ERROR,
                        statusMessage = "$error. Inicie el escritorio con enlace real y pulse Conectar en la PC."
                    ).withLog(ActivityCategory.CONNECTION, "Falla del enlace real: $error")
                }
                return@launch
            }
            _uiState.update { state ->
                state.copy(
                    status = ConnectionStatus.CONNECTED,
                    connectedDesktop = desktop,
                    activeTransport = TransportType.USB_CABLE,
                    pairingPin = null,
                    linkTelemetry = LinkTelemetry(signalQualityPercent = 100),
                    statusMessage = "Enlazado por cable USB; compara el PIN con el ordenador"
                ).withLog(ActivityCategory.CONNECTION, "Enlace real abierto con ${desktop.deviceName}")
            }
            startRealSessionStreams()
        }
    }

    /**
     * Procesa los eventos del ordenador y espeja las notificaciones del teléfono mientras el enlace real siga abierto.
     */
    private fun startRealSessionStreams() {
        sessionJobs.forEach { it.cancel() }
        sessionJobs = listOf(
            viewModelScope.launch {
                socketLinkRepository.events().collect { event -> applyDesktopLinkEvent(event) }
            },
            viewModelScope.launch {
                if (_uiState.value.featureToggles.syncNotifications) {
                    _uiState.value.notifications.filterNot { it.isResolved }.forEach { socketLinkRepository.publishNotification(it) }
                }
                notificationSource().incomingNotifications().collect { item ->
                    if (_uiState.value.featureToggles.syncNotifications) {
                        socketLinkRepository.publishNotification(item)
                        _uiState.update { state ->
                            state.copy(notifications = listOf(item) + state.notifications.filterNot { it.notificationId == item.notificationId })
                                .withLog(ActivityCategory.NOTIFICATION, "${item.appName} espejada al ordenador")
                        }
                    }
                }
            }
        ) + listOfNotNull(systemClipboardRepository?.let { clipboard ->
            viewModelScope.launch {
                clipboard.changes().collect { text -> publishPhoneClipboardChange(text) }
            }
        })
    }

    /**
     * Envía al ordenador el texto copiado en el teléfono, salvo que sea el mismo que acaba de llegar desde la PC.
     */
    private suspend fun publishPhoneClipboardChange(text: String) {
        val state = _uiState.value
        if (!state.featureToggles.syncClipboard || text == lastClipboardFromDesktop || text == state.clipboardText) {
            return
        }
        socketLinkRepository.publishClipboard(text)
        _uiState.update { current ->
            current.copy(
                clipboardText = text,
                clipboardHistory = (listOf(ClipboardHistoryEntry(text, fromMobile = true, epochMillis = clock())) + current.clipboardHistory).take(8)
            ).withLog(ActivityCategory.CLIPBOARD, "Copia del teléfono enviada al ordenador (${text.length} caracteres)")
        }
    }

    /**
     * Fuente de notificaciones vigente: la del sistema con el enlace real (si existe) y la simulada en otro caso.
     */
    private fun notificationSource(): NotificationBridgeSource =
        systemNotificationRepository?.takeIf { _uiState.value.useRealLink } ?: notificationRepository

    /**
     * Aplica al estado un evento recibido del ordenador por el enlace real.
     */
    private suspend fun applyDesktopLinkEvent(event: DesktopLinkEvent) {
        when (event) {
            is DesktopLinkEvent.Hello -> _uiState.update { state ->
                state.copy(pairingPin = event.pairingPin).withLog(ActivityCategory.CONNECTION, "Saludo recibido de ${event.desktopName}")
            }
            is DesktopLinkEvent.ClipboardFromDesktop -> {
                if (_uiState.value.featureToggles.syncClipboard) {
                    lastClipboardFromDesktop = event.text
                    systemClipboardRepository?.write(event.text)
                }
                _uiState.update { state ->
                    if (!state.featureToggles.syncClipboard) {
                        state
                    } else {
                        state.copy(
                            clipboardText = event.text,
                            clipboardHistory = (listOf(ClipboardHistoryEntry(event.text, fromMobile = false, epochMillis = clock())) + state.clipboardHistory).take(8)
                        ).withLog(ActivityCategory.CLIPBOARD, "Texto recibido del ordenador")
                    }
                }
            }
            is DesktopLinkEvent.Resolution -> {
                val updated = notificationSource().resolveNotificationFromDesktop(event.action)
                _uiState.update { state ->
                    state.copy(notifications = updated).withLog(ActivityCategory.NOTIFICATION, "Resolución recibida desde la PC")
                }
            }
            DesktopLinkEvent.Heartbeat -> _uiState.update { state ->
                state.copy(linkTelemetry = state.linkTelemetry.copy(framesReceived = state.linkTelemetry.framesReceived + 1))
            }
            is DesktopLinkEvent.Closed -> {
                _uiState.update { state ->
                    state.copy(
                        status = ConnectionStatus.DISCONNECTED,
                        connectedDesktop = null,
                        activeTransport = null,
                        pairingPin = null,
                        linkTelemetry = LinkTelemetry(),
                        statusMessage = event.reason
                    ).withLog(ActivityCategory.CONNECTION, "Enlace real cerrado: ${event.reason}")
                }
                stopAllStreams()
            }
        }
    }

    /**
     * Mantiene el reloj de cuadros solo mientras alguna vista previa animada lo necesite.
     */
    private fun refreshFrameClock() {
        val toggles = _uiState.value.featureToggles
        val needsClock = toggles.musicMode.enabled || toggles.remoteCameraMic.cameraEnabled || toggles.secondaryDisplay.enabled
        if (needsClock && frameJob?.isActive != true) {
            frameJob = viewModelScope.launch {
                mediaStreamRepository.frameTicks().collect { tick -> _uiState.update { it.copy(frameTick = tick) } }
            }
        } else if (!needsClock) {
            frameJob?.cancel()
            frameJob = null
        }
    }

    /**
     * Cancela todos los flujos de sesión y multimedia.
     */
    private fun stopAllStreams() {
        (sessionJobs + listOfNotNull(musicJob, microphoneJob, frameJob)).forEach { it.cancel() }
        sessionJobs = emptyList()
        musicJob = null
        microphoneJob = null
        frameJob = null
    }

    /**
     * Agrega una entrada a la bitácora de actividad conservando las 40 más recientes.
     */
    private fun AndroidConnectionUiState.withLog(category: ActivityCategory, message: String): AndroidConnectionUiState =
        copy(activityLog = (activityLog + ActivityLogEntry(clock(), category, message)).takeLast(40))

    companion object {
        /**
         * Construye una instancia por defecto del ViewModel inyectando los repositorios de la capa Model; sin `context`
         * (pruebas) se omiten las integraciones con el sistema Android.
         */
        fun createDefault(context: Context? = null): AndroidConnectionViewModel {
            val rustCore = AndroidNativeCoreLoader.loadOrDummy()
            return AndroidConnectionViewModel(
                deviceRepository = AndroidDeviceRepository(rustCore),
                notificationRepository = AndroidNotificationBridgeRepository(),
                mediaStreamRepository = AndroidMediaStreamRepository(rustCore),
                pairingRepository = AndroidPairingRepository(rustCore),
                socketLinkRepository = AndroidSocketLinkRepository(rustCore),
                systemNotificationRepository = context?.let { AndroidSystemNotificationRepository(it) },
                systemClipboardRepository = context?.let { AndroidSystemClipboardRepository(it) }
            )
        }

        /**
         * Fábrica compatible con `viewModel()` para que el ViewModel sobreviva a cambios de configuración.
         */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { createDefault(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) }
        }
    }
}
