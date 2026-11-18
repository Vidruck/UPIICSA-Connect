package mx.ipn.upiicsa.connect.desktop.application

import mx.ipn.upiicsa.connect.desktop.domain.DesktopSessionAggregate
import mx.ipn.upiicsa.connect.desktop.domain.DesktopSessionState
import mx.ipn.upiicsa.connect.desktop.ports.inbound.ClipboardSyncUseCase
import mx.ipn.upiicsa.connect.desktop.ports.inbound.ConnectDeviceUseCase
import mx.ipn.upiicsa.connect.desktop.ports.inbound.MusicModeUseCase
import mx.ipn.upiicsa.connect.desktop.ports.inbound.NotificationResolutionUseCase
import mx.ipn.upiicsa.connect.desktop.ports.inbound.SecondaryMonitorUseCase
import mx.ipn.upiicsa.connect.desktop.ports.inbound.VirtualCameraMicUseCase
import mx.ipn.upiicsa.connect.desktop.ports.outbound.DeviceTransportOutboundPort
import mx.ipn.upiicsa.connect.desktop.ports.outbound.OsAudioSinkOutboundPort
import mx.ipn.upiicsa.connect.desktop.ports.outbound.OsClipboardOutboundPort
import mx.ipn.upiicsa.connect.desktop.ports.outbound.OsNotificationOutboundPort
import mx.ipn.upiicsa.connect.desktop.ports.outbound.OsVirtualCameraMicOutboundPort
import mx.ipn.upiicsa.connect.desktop.ports.outbound.OsVirtualDisplayOutboundPort
import mx.ipn.upiicsa.connect.shared.model.ConnectionDirection
import mx.ipn.upiicsa.connect.shared.model.NotificationResolutionAction
import mx.ipn.upiicsa.connect.shared.model.PhoneNotificationItem
import mx.ipn.upiicsa.connect.shared.model.RemoteDeviceDescriptor
import mx.ipn.upiicsa.connect.shared.model.SecondaryDisplayConfig
import mx.ipn.upiicsa.connect.shared.model.TransportType
import mx.ipn.upiicsa.connect.shared.protocol.RustCoreBridgeContract

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
    private val audioSinkPort: OsAudioSinkOutboundPort
) : ConnectDeviceUseCase,
    NotificationResolutionUseCase,
    ClipboardSyncUseCase,
    SecondaryMonitorUseCase,
    VirtualCameraMicUseCase,
    MusicModeUseCase {

    /**
     * Consulta los dispositivos disponibles ordenando primero los accesibles por cable USB.
     */
    override suspend fun discoverAvailableDevices(): List<RemoteDeviceDescriptor> {
        return transportPort.discoverDevices().sortedBy { it.preferredTransport.priorityRank }
    }

    /**
     * Negocia el mejor medio mediante el núcleo Rust y abre la sesión hacia o desde el dispositivo Android.
     */
    override suspend fun connectToDevice(
        device: RemoteDeviceDescriptor,
        preferredTransport: TransportType?,
        direction: ConnectionDirection
    ): DesktopSessionState {
        val chosenTransport = preferredTransport ?: rustCore.selectBestTransport(
            usbAvailable = device.availableTransports.contains(TransportType.USB_CABLE),
            wifiAvailable = device.availableTransports.contains(TransportType.WIFI_LAN),
            bluetoothAvailable = device.availableTransports.contains(TransportType.BLUETOOTH)
        ) ?: TransportType.USB_CABLE

        transportPort.connect(device, chosenTransport, direction)
        return aggregate.establishConnection(device, chosenTransport, direction)
    }

    /**
     * Detiene todos los flujos activos de periféricos virtuales y cierra el canal físico.
     */
    override suspend fun disconnect(): DesktopSessionState {
        virtualDisplayPort.stopVirtualDisplay()
        virtualCameraMicPort.enableVirtualDevices(enableCamera = false, enableMicrophone = false)
        audioSinkPort.closeMusicModeSink()
        transportPort.disconnect()
        return aggregate.terminateConnection()
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
        return aggregate.upsertNotifications(items)
    }

    /**
     * Ejecuta la resolución de una notificación móvil desde la PC y la envía por el transporte prioritario.
     */
    override suspend fun resolveNotification(action: NotificationResolutionAction): DesktopSessionState {
        notificationPort.dispatchResolutionFeedback(action)

        val (targetId, summary, wireText) = when (action) {
            is NotificationResolutionAction.QuickReply -> Triple(
                action.notificationId,
                "Respondida: \"${action.replyText}\"",
                "REPLY|${action.notificationId}|${action.replyText}"
            )
            is NotificationResolutionAction.MarkAsRead -> Triple(
                action.notificationId,
                "Marcada como leída",
                "READ|${action.notificationId}"
            )
            is NotificationResolutionAction.Dismiss -> Triple(
                action.notificationId,
                "Descartada desde PC",
                "DISMISS|${action.notificationId}"
            )
        }

        transportPort.sendChannelPayload(channelId = 1, payload = wireText.encodeToByteArray())
        return aggregate.markNotificationResolved(targetId, summary)
    }

    /**
     * Sincroniza texto hacia el portapapeles del sistema operativo anfitrión y hacia Android.
     */
    override suspend fun pushClipboardText(text: String): DesktopSessionState {
        clipboardPort.writeClipboardText(text)
        transportPort.sendChannelPayload(channelId = 2, payload = text.encodeToByteArray())
        return aggregate.updateClipboard(text)
    }

    /**
     * Obtiene el contenido actual del portapapeles nativo del equipo de escritorio.
     */
    override suspend fun pullOsClipboardText(): String {
        return clipboardPort.readClipboardText()
    }

    /**
     * Configura el monitor virtual secundario y captura un cuadro inicial de comprobación cuando está activo.
     */
    override suspend fun toggleSecondaryDisplay(config: SecondaryDisplayConfig): DesktopSessionState {
        if (config.enabled) {
            virtualDisplayPort.startVirtualDisplay(config)
            val initialFrame = virtualDisplayPort.captureCurrentFrame()
            transportPort.sendChannelPayload(channelId = 3, payload = initialFrame)
        } else {
            virtualDisplayPort.stopVirtualDisplay()
        }
        return aggregate.updateFeatures { toggles ->
            toggles.copy(secondaryDisplay = config)
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
        val commandPayload = "CAM:$enableCamera|MIC:$enableMicrophone|BACK:$useBackCamera"
        transportPort.sendChannelPayload(channelId = 4, payload = commandPayload.encodeToByteArray())
        return aggregate.updateFeatures { toggles ->
            toggles.copy(
                remoteCameraMic = toggles.remoteCameraMic.copy(
                    cameraEnabled = enableCamera,
                    microphoneEnabled = enableMicrophone,
                    useBackCamera = useBackCamera
                )
            )
        }
    }

    /**
     * Habilita Music Mode abriendo la tarjeta de sonido del PC y aplicando procesamiento DSP del core Rust.
     */
    override suspend fun toggleMusicMode(
        enabled: Boolean,
        gainBoostFactor: Float,
        trackTitle: String
    ): DesktopSessionState {
        if (enabled) {
            audioSinkPort.openMusicModeSink(sampleRateHz = 48_000)
            // [DUMMY-PRUEBAS]: Bloque PCM sintético de prueba para validar el paso por el DSP de Music Mode hacia la tarjeta de PC.
            val sampleBlock = shortArrayOf(900, -900, 1800, -1800)
            rustCore.processMusicModePcm(sampleBlock, gainBoostFactor)
            audioSinkPort.writePcmSamples(sampleBlock)
        } else {
            audioSinkPort.closeMusicModeSink()
        }
        transportPort.sendChannelPayload(
            channelId = 5,
            payload = "MUSIC_MODE:$enabled|GAIN:$gainBoostFactor".encodeToByteArray()
        )
        return aggregate.updateFeatures { toggles ->
            toggles.copy(
                musicMode = toggles.musicMode.copy(
                    enabled = enabled,
                    gainBoostFactor = gainBoostFactor,
                    activeTrackTitle = trackTitle
                )
            )
        }
    }
}
