package mx.ipn.upiicsa.connect.desktop.application

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mx.ipn.upiicsa.connect.desktop.domain.DesktopSessionAggregate
import mx.ipn.upiicsa.connect.desktop.domain.DesktopSessionState
import mx.ipn.upiicsa.connect.desktop.domain.PhoneEvent
import mx.ipn.upiicsa.connect.desktop.ports.inbound.CoreRuntimeInfo
import mx.ipn.upiicsa.connect.desktop.ports.inbound.DesktopUseCases
import mx.ipn.upiicsa.connect.desktop.ports.outbound.DeviceTransportOutboundPort
import mx.ipn.upiicsa.connect.desktop.ports.outbound.OsAudioSinkOutboundPort
import mx.ipn.upiicsa.connect.desktop.ports.outbound.OsClipboardOutboundPort
import mx.ipn.upiicsa.connect.desktop.ports.outbound.OsNotificationOutboundPort
import mx.ipn.upiicsa.connect.desktop.ports.outbound.OsVirtualCameraMicOutboundPort
import mx.ipn.upiicsa.connect.desktop.ports.outbound.OsVirtualDisplayOutboundPort
import mx.ipn.upiicsa.connect.desktop.ports.outbound.PhoneEventStreamOutboundPort
import mx.ipn.upiicsa.connect.shared.model.ActivityCategory
import mx.ipn.upiicsa.connect.shared.model.ConnectionDirection
import mx.ipn.upiicsa.connect.shared.model.ConnectionStatus
import mx.ipn.upiicsa.connect.shared.model.NotificationResolutionAction
import mx.ipn.upiicsa.connect.shared.model.PhoneNotificationItem
import mx.ipn.upiicsa.connect.shared.model.RemoteDeviceDescriptor
import mx.ipn.upiicsa.connect.shared.model.SecondaryDisplayConfig
import mx.ipn.upiicsa.connect.shared.model.TransportType
import mx.ipn.upiicsa.connect.shared.protocol.ChannelMessage
import mx.ipn.upiicsa.connect.shared.protocol.ChannelMessageCodec
import mx.ipn.upiicsa.connect.shared.protocol.ContentOrigin
import mx.ipn.upiicsa.connect.shared.protocol.RustCoreBridgeContract
import mx.ipn.upiicsa.connect.shared.protocol.ServiceChannel
import mx.ipn.upiicsa.connect.shared.util.DisplayFormatters
import kotlin.math.roundToInt

/**
 * Servicio de aplicación hexagonal que orquesta los casos de uso de escritorio mediante puertos segregados sin acoplarse al S.O.
 */
class DesktopConnectionService(
    private val aggregate: DesktopSessionAggregate,
    private val rustCore: RustCoreBridgeContract,
    private val transportPort: DeviceTransportOutboundPort,
    private val notificationPort: OsNotificationOutboundPort,
    private val clipboardPort: OsClipboardOutboundPort,
    private val virtualDisplayPort: OsVirtualDisplayOutboundPort,
    private val virtualCameraMicPort: OsVirtualCameraMicOutboundPort,
    private val audioSinkPort: OsAudioSinkOutboundPort,
    private val phoneEventPort: PhoneEventStreamOutboundPort,
    private val localDeviceId: String = "pc-upiicsa-escritorio",
    private val linkModeLabel: String = "Simulado (teléfono virtual)",
    private val clock: () -> Long = System::currentTimeMillis
) : DesktopUseCases {

    private val mutex = Mutex()
    private val mutableSessionState = MutableStateFlow(aggregate.snapshot())

    override val sessionState: StateFlow<DesktopSessionState> = mutableSessionState.asStateFlow()

    /**
     * Describe el núcleo nativo inyectado (binario Rust vía JNA o simulación Kotlin).
     */
    override fun coreRuntimeInfo(): CoreRuntimeInfo =
        CoreRuntimeInfo(version = rustCore.coreVersion(), nativeBacked = rustCore.isNativeBacked, linkModeLabel = linkModeLabel)

    /**
     * Consulta los dispositivos disponibles ordenando primero los accesibles por cable USB.
     */
    override suspend fun discoverAvailableDevices(): List<RemoteDeviceDescriptor> {
        mutate { beginDiscovery() }
        val devices = transportPort.discoverDevices().sortedBy { it.preferredTransport.priorityRank }
        mutate {
            finishDiscovery(devices.size)
            log(clock(), ActivityCategory.CONNECTION, "Búsqueda completada: ${devices.size} dispositivo(s)")
        }
        return devices
    }

    /**
     * Negocia el mejor medio mediante el núcleo Rust, deriva el PIN de emparejamiento y abre la sesión con el dispositivo.
     */
    override suspend fun connectToDevice(
        device: RemoteDeviceDescriptor,
        preferredTransport: TransportType?,
        direction: ConnectionDirection
    ): DesktopSessionState {
        val chosenTransport = preferredTransport?.takeIf { device.availableTransports.contains(it) }
            ?: rustCore.selectBestTransport(
                usbAvailable = device.availableTransports.contains(TransportType.USB_CABLE),
                wifiAvailable = device.availableTransports.contains(TransportType.WIFI_LAN),
                bluetoothAvailable = device.availableTransports.contains(TransportType.BLUETOOTH)
            )
            ?: TransportType.USB_CABLE

        if (!transportPort.connect(device, chosenTransport, direction)) {
            return mutate {
                reportFailure("No fue posible enlazar con ${device.deviceName}")
                log(clock(), ActivityCategory.CONNECTION, "Falla al enlazar ${device.deviceName}")
            }
        }
        val pin = rustCore.derivePairingPin(localDeviceId, device.deviceId, clock() / PIN_ROTATION_MS)
        val state = mutate {
            establishConnection(device, chosenTransport, direction, pin)
            log(
                clock(),
                ActivityCategory.CONNECTION,
                "Enlace ${direction.displayLabel} con ${device.deviceName} vía ${chosenTransport.shortLabel} " +
                    "(PIN ${DisplayFormatters.pairingPin(pin)})"
            )
        }
        sendMessage(ChannelMessage.HandshakeHello(direction, "UPIICSA-Connect Escritorio", pin))
        return state
    }

    /**
     * Detiene todos los flujos activos de periféricos virtuales y cierra el canal físico.
     */
    override suspend fun disconnect(): DesktopSessionState {
        virtualDisplayPort.stopVirtualDisplay()
        virtualCameraMicPort.enableVirtualDevices(enableCamera = false, enableMicrophone = false)
        audioSinkPort.closeMusicModeSink()
        transportPort.disconnect()
        return mutate {
            terminateConnection()
            log(clock(), ActivityCategory.CONNECTION, "Sesión finalizada y periféricos virtuales liberados")
        }
    }

    /**
     * Publica en el sistema operativo anfitrión las notificaciones espejadas desde Android.
     */
    override suspend fun syncIncomingNotifications(items: List<PhoneNotificationItem>): DesktopSessionState {
        for (item in items) {
            if (!item.isResolved) {
                notificationPort.publishPhoneNotification(item)
            }
        }
        return mutate { upsertNotifications(items) }
    }

    /**
     * Ejecuta la resolución de una notificación móvil desde la PC y la envía por el transporte prioritario.
     */
    override suspend fun resolveNotification(action: NotificationResolutionAction): DesktopSessionState {
        notificationPort.dispatchResolutionFeedback(action)
        sendMessage(ChannelMessage.fromResolution(action))
        val (targetId, summary) = when (action) {
            is NotificationResolutionAction.QuickReply -> action.notificationId to "Respondida: \"${action.replyText}\""
            is NotificationResolutionAction.MarkAsRead -> action.notificationId to "Marcada como leída"
            is NotificationResolutionAction.Dismiss -> action.notificationId to "Descartada desde PC"
        }
        return mutate {
            markNotificationResolved(targetId, summary)
            log(clock(), ActivityCategory.NOTIFICATION, summary)
        }
    }

    /**
     * Activa o pausa el espejo automático de notificaciones del teléfono.
     */
    override suspend fun setNotificationMirroring(enabled: Boolean): DesktopSessionState = mutate {
        updateFeatures { it.copy(syncNotifications = enabled) }
        log(clock(), ActivityCategory.NOTIFICATION, if (enabled) "Espejo de notificaciones activo" else "Espejo de notificaciones pausado")
    }

    /**
     * Sincroniza texto hacia el portapapeles del sistema operativo anfitrión y hacia Android.
     */
    override suspend fun pushClipboardText(text: String): DesktopSessionState {
        if (text.isBlank()) {
            return aggregate.snapshot()
        }
        clipboardPort.writeClipboardText(text)
        sendMessage(ChannelMessage.ClipboardText(ContentOrigin.DESKTOP, text))
        return mutate {
            updateClipboard(text, fromMobile = false, epochMillis = clock())
            log(clock(), ActivityCategory.CLIPBOARD, "Texto enviado al teléfono (${text.length} caracteres)")
        }
    }

    /**
     * Obtiene el contenido actual del portapapeles nativo del equipo de escritorio.
     */
    override suspend fun pullOsClipboardText(): String {
        return clipboardPort.readClipboardText()
    }

    /**
     * Activa o pausa la sincronización automática del portapapeles.
     */
    override suspend fun setClipboardSync(enabled: Boolean): DesktopSessionState = mutate {
        updateFeatures { it.copy(syncClipboard = enabled) }
        log(clock(), ActivityCategory.CLIPBOARD, if (enabled) "Portapapeles compartido activo" else "Portapapeles compartido pausado")
    }

    /**
     * Configura el monitor virtual secundario y envía un cuadro inicial de comprobación cuando está activo.
     */
    override suspend fun toggleSecondaryDisplay(config: SecondaryDisplayConfig): DesktopSessionState {
        if (config.enabled) {
            virtualDisplayPort.startVirtualDisplay(config)
            transportPort.sendChannelPayload(ServiceChannel.SECONDARY_DISPLAY_VIDEO, virtualDisplayPort.captureCurrentFrame())
        } else {
            virtualDisplayPort.stopVirtualDisplay()
        }
        return mutate {
            updateFeatures { it.copy(secondaryDisplay = config) }
            log(
                clock(),
                ActivityCategory.DISPLAY,
                if (config.enabled) {
                    "Monitor secundario ${config.widthPx}×${config.heightPx} @ ${config.targetFps} fps"
                } else {
                    "Monitor secundario desactivado"
                }
            )
        }
    }

    /**
     * Activa o desactiva la cámara y micrófono virtuales del portátil alimentados por el móvil.
     */
    override suspend fun toggleVirtualCameraAndMic(
        enableCamera: Boolean,
        enableMicrophone: Boolean,
        useBackCamera: Boolean
    ): DesktopSessionState {
        virtualCameraMicPort.enableVirtualDevices(enableCamera, enableMicrophone)
        sendMessage(ChannelMessage.CameraMicCommand(enableCamera, enableMicrophone, useBackCamera))
        return mutate {
            updateFeatures { toggles ->
                toggles.copy(
                    remoteCameraMic = toggles.remoteCameraMic.copy(
                        cameraEnabled = enableCamera,
                        microphoneEnabled = enableMicrophone,
                        useBackCamera = useBackCamera
                    )
                )
            }
            if (!enableMicrophone) {
                updateMediaTelemetry { it.copy(microphoneLevel = 0f) }
            }
            log(
                clock(),
                ActivityCategory.MEDIA,
                "Cámara ${if (enableCamera) "activa" else "apagada"} · Micrófono ${if (enableMicrophone) "activo" else "apagado"}"
            )
        }
    }

    /**
     * Habilita Music Mode abriendo la tarjeta de sonido del PC; los bloques PCM llegan después por el flujo del teléfono.
     */
    override suspend fun toggleMusicMode(
        enabled: Boolean,
        gainBoostFactor: Float,
        trackTitle: String
    ): DesktopSessionState {
        val wasEnabled = aggregate.snapshot().featureToggles.musicMode.enabled
        if (enabled && !wasEnabled) {
            audioSinkPort.openMusicModeSink(sampleRateHz = MUSIC_SAMPLE_RATE_HZ)
        } else if (!enabled) {
            audioSinkPort.closeMusicModeSink()
        }
        sendMessage(ChannelMessage.MusicModeCommand(enabled, (gainBoostFactor * 1000).roundToInt()))
        return mutate {
            updateFeatures { toggles ->
                toggles.copy(
                    musicMode = toggles.musicMode.copy(
                        enabled = enabled,
                        gainBoostFactor = gainBoostFactor,
                        activeTrackTitle = trackTitle
                    )
                )
            }
            if (!enabled) {
                updateMediaTelemetry { it.copy(musicLevel = 0f) }
            }
            log(
                clock(),
                ActivityCategory.MEDIA,
                if (enabled) {
                    "Music Mode activo (ganancia ${DisplayFormatters.decimal(gainBoostFactor)}x)"
                } else {
                    "Music Mode detenido"
                }
            )
        }
    }

    /**
     * Aplica un evento del teléfono respetando los interruptores activos; se ignora si no hay sesión conectada.
     */
    override suspend fun processPhoneEvent(event: PhoneEvent): DesktopSessionState {
        val current = aggregate.snapshot()
        if (current.status != ConnectionStatus.CONNECTED) {
            return current
        }
        val toggles = current.featureToggles
        return when (event) {
            is PhoneEvent.IncomingNotification -> {
                if (!toggles.syncNotifications) {
                    return current
                }
                notificationPort.publishPhoneNotification(event.item)
                mutate {
                    addIncomingNotification(event.item)
                    log(clock(), ActivityCategory.NOTIFICATION, "${event.item.appName}: ${event.item.senderTitle}")
                }
            }
            is PhoneEvent.ClipboardFromPhone -> {
                if (!toggles.syncClipboard) {
                    return current
                }
                clipboardPort.writeClipboardText(event.text)
                mutate {
                    updateClipboard(event.text, fromMobile = true, epochMillis = clock())
                    log(clock(), ActivityCategory.CLIPBOARD, "Texto recibido del teléfono (${event.text.length} caracteres)")
                }
            }
            is PhoneEvent.MusicPcmBlock -> {
                if (!toggles.musicMode.enabled) {
                    return mutate { updateMediaTelemetry { it.copy(musicLevel = 0f) } }
                }
                val metrics = rustCore.processMusicModePcm(event.samples, toggles.musicMode.gainBoostFactor)
                audioSinkPort.writePcmSamples(event.samples)
                mutate {
                    if (toggles.musicMode.activeTrackTitle != event.trackTitle) {
                        updateFeatures { it.copy(musicMode = it.musicMode.copy(activeTrackTitle = event.trackTitle)) }
                    }
                    updateMediaTelemetry { it.copy(musicLevel = metrics.peakNormalized.coerceIn(0f, 1f)) }
                }
            }
            is PhoneEvent.MicrophonePcmBlock -> {
                if (!toggles.remoteCameraMic.microphoneEnabled) {
                    return current
                }
                virtualCameraMicPort.pushMicAudio(event.samples)
                mutate { updateMediaTelemetry { it.copy(microphoneLevel = event.level.coerceIn(0f, 1f)) } }
            }
            is PhoneEvent.CameraFrame -> {
                if (!toggles.remoteCameraMic.cameraEnabled) {
                    return current
                }
                virtualCameraMicPort.pushVideoFrame(event.encodedFrame)
                mutate {
                    updateLinkTelemetry { it.copy(framesReceived = it.framesReceived + 1) }
                    updateMediaTelemetry { it.copy(cameraFramesReceived = it.cameraFramesReceived + 1) }
                }
            }
            is PhoneEvent.DisplayVsync -> {
                if (!toggles.secondaryDisplay.enabled) {
                    return current
                }
                val frame = virtualDisplayPort.captureCurrentFrame()
                transportPort.sendChannelPayload(ServiceChannel.SECONDARY_DISPLAY_VIDEO, frame)
                mutate {
                    updateLinkTelemetry { it.copy(framesSent = it.framesSent + 1) }
                    updateMediaTelemetry { it.copy(displayFramesSent = it.displayFramesSent + 1) }
                }
            }
            is PhoneEvent.LinkClosed -> {
                mutate { log(clock(), ActivityCategory.CONNECTION, "Enlace cerrado: ${event.reason}") }
                disconnect()
            }
            is PhoneEvent.LinkHeartbeat -> mutate {
                updateLinkTelemetry {
                    it.copy(
                        latencyMs = event.latencyMs,
                        signalQualityPercent = event.signalQualityPercent,
                        framesReceived = it.framesReceived + 1
                    )
                }
            }
        }
    }

    /**
     * Suscribe el flujo de eventos del teléfono a la sesión activa; al cambiar o cerrar la sesión el flujo anterior se cancela.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun runPhoneEventLoop() {
        sessionState
            .map { it.activeDevice to it.activeTransport }
            .distinctUntilChanged()
            .flatMapLatest { (device, transport) ->
                if (device == null || transport == null) emptyFlow() else phoneEventPort.events(device, transport)
            }
            .collect { event -> processPhoneEvent(event) }
    }

    /**
     * Codifica un mensaje de protocolo, lo envía por su canal lógico y contabiliza la trama si el transporte la acepta.
     */
    private suspend fun sendMessage(message: ChannelMessage) {
        val sent = transportPort.sendChannelPayload(message.channel, ChannelMessageCodec.encode(message))
        if (sent) {
            mutate { updateLinkTelemetry { it.copy(framesSent = it.framesSent + 1) } }
        }
    }

    /**
     * Aplica una transición sobre el agregado de forma exclusiva y publica el nuevo estado observable.
     */
    private suspend fun mutate(transition: DesktopSessionAggregate.() -> DesktopSessionState): DesktopSessionState =
        mutex.withLock {
            aggregate.transition().also { mutableSessionState.value = it }
        }

    private companion object {
        const val MUSIC_SAMPLE_RATE_HZ = 48_000
        const val PIN_ROTATION_MS = 30_000L
    }
}
