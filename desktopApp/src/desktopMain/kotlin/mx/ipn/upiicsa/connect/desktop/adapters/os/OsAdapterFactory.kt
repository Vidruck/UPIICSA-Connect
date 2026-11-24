package mx.ipn.upiicsa.connect.desktop.adapters.os

import mx.ipn.upiicsa.connect.desktop.adapters.nativecore.NativeCoreLoader
import mx.ipn.upiicsa.connect.desktop.adapters.os.linux.LinuxClipboardAdapter
import mx.ipn.upiicsa.connect.desktop.adapters.os.linux.LinuxFreedesktopNotificationAdapter
import mx.ipn.upiicsa.connect.desktop.adapters.os.linux.LinuxPipeWireAudioSinkAdapter
import mx.ipn.upiicsa.connect.desktop.adapters.os.linux.LinuxV4l2CameraMicAdapter
import mx.ipn.upiicsa.connect.desktop.adapters.os.linux.LinuxVirtualDisplayAdapter
import mx.ipn.upiicsa.connect.desktop.adapters.os.windows.WindowsDirectShowCameraMicAdapter
import mx.ipn.upiicsa.connect.desktop.adapters.os.windows.WindowsIddCxDisplayAdapter
import mx.ipn.upiicsa.connect.desktop.adapters.os.windows.WindowsWasapiAudioSinkAdapter
import mx.ipn.upiicsa.connect.desktop.adapters.os.windows.WindowsWin32ClipboardAdapter
import mx.ipn.upiicsa.connect.desktop.adapters.os.windows.WindowsWinRtNotificationAdapter
import mx.ipn.upiicsa.connect.desktop.adapters.simulation.DummyPhoneEventStreamAdapter
import mx.ipn.upiicsa.connect.desktop.adapters.preferences.FileDesktopPreferencesAdapter
import mx.ipn.upiicsa.connect.desktop.adapters.process.SystemProcessRunner
import mx.ipn.upiicsa.connect.desktop.adapters.transport.DesktopLinkMode
import mx.ipn.upiicsa.connect.desktop.adapters.transport.MultiChannelTransportAdapter
import mx.ipn.upiicsa.connect.desktop.adapters.transport.tcp.AdbDeviceBridge
import mx.ipn.upiicsa.connect.desktop.adapters.transport.tcp.SocketPhoneEventStreamAdapter
import mx.ipn.upiicsa.connect.desktop.adapters.transport.tcp.TcpLinkSession
import mx.ipn.upiicsa.connect.desktop.adapters.transport.tcp.TcpLinkTransportAdapter
import mx.ipn.upiicsa.connect.desktop.application.DesktopConnectionService
import mx.ipn.upiicsa.connect.desktop.domain.DesktopSessionAggregate
import mx.ipn.upiicsa.connect.desktop.ports.inbound.LinkModeInfo
import mx.ipn.upiicsa.connect.desktop.ports.outbound.DesktopPreferencesOutboundPort
import mx.ipn.upiicsa.connect.desktop.ports.outbound.OsAudioSinkOutboundPort
import mx.ipn.upiicsa.connect.desktop.ports.outbound.OsClipboardOutboundPort
import mx.ipn.upiicsa.connect.desktop.ports.outbound.OsNotificationOutboundPort
import mx.ipn.upiicsa.connect.desktop.ports.outbound.OsVirtualCameraMicOutboundPort
import mx.ipn.upiicsa.connect.desktop.ports.outbound.OsVirtualDisplayOutboundPort
import mx.ipn.upiicsa.connect.desktop.ports.outbound.DeviceTransportOutboundPort
import mx.ipn.upiicsa.connect.desktop.ports.outbound.PhoneEventStreamOutboundPort
import mx.ipn.upiicsa.connect.shared.model.DesktopOsPlatform
import mx.ipn.upiicsa.connect.shared.protocol.RustCoreBridgeContract

/**
 * Agrupa el conjunto de puertos de salida instanciados para el sistema operativo anfitrión sin mezclar implementaciones.
 */
data class OsPortBundle(
    val platform: DesktopOsPlatform,
    val notificationPort: OsNotificationOutboundPort,
    val clipboardPort: OsClipboardOutboundPort,
    val virtualDisplayPort: OsVirtualDisplayOutboundPort,
    val virtualCameraMicPort: OsVirtualCameraMicOutboundPort,
    val audioSinkPort: OsAudioSinkOutboundPort
)

/**
 * Fábrica de ensamblaje que detecta Windows o GNU/Linux e inyecta los adaptadores dedicados correspondientes.
 */
object OsAdapterFactory {

    /**
     * Detecta la plataforma de escritorio actual a partir de las propiedades de la máquina virtual JVM.
     */
    fun detectHostPlatform(): DesktopOsPlatform {
        val osName = System.getProperty("os.name").orEmpty().lowercase()
        return if (osName.contains("win")) {
            DesktopOsPlatform.WINDOWS
        } else {
            DesktopOsPlatform.GNU_LINUX
        }
    }

    /**
     * Construye el paquete de puertos de salida desacoplado correspondiente al sistema operativo indicado.
     * Con el enlace real se usan el portapapeles y las notificaciones del sistema; con el simulador quedan en memoria.
     */
    fun createPortBundle(
        platform: DesktopOsPlatform = detectHostPlatform(),
        linkMode: DesktopLinkMode = DesktopLinkMode.SIMULATED
    ): OsPortBundle {
        // [DECISIÓN TÉCNICA]: El teléfono virtual publica textos y notificaciones de ejemplo; para no sobrescribir el portapapeles real ni llenar el escritorio de avisos, solo el enlace real usa esas integraciones. El audio sí es real en ambos modos porque solo suena al activar Music Mode.
        val systemIntegration = linkMode == DesktopLinkMode.REAL_TCP
        return when (platform) {
            DesktopOsPlatform.GNU_LINUX -> OsPortBundle(
                platform = DesktopOsPlatform.GNU_LINUX,
                notificationPort = if (systemIntegration) LinuxFreedesktopNotificationAdapter.forHost() else LinuxFreedesktopNotificationAdapter(),
                clipboardPort = if (systemIntegration) LinuxClipboardAdapter.forHost() else LinuxClipboardAdapter(),
                virtualDisplayPort = LinuxVirtualDisplayAdapter(),
                virtualCameraMicPort = LinuxV4l2CameraMicAdapter(),
                audioSinkPort = LinuxPipeWireAudioSinkAdapter.forHost()
            )
            DesktopOsPlatform.WINDOWS -> OsPortBundle(
                platform = DesktopOsPlatform.WINDOWS,
                notificationPort = if (systemIntegration) WindowsWinRtNotificationAdapter.forHost() else WindowsWinRtNotificationAdapter(),
                clipboardPort = if (systemIntegration) WindowsWin32ClipboardAdapter.forHost() else WindowsWin32ClipboardAdapter(),
                virtualDisplayPort = WindowsIddCxDisplayAdapter(),
                virtualCameraMicPort = WindowsDirectShowCameraMicAdapter(),
                audioSinkPort = WindowsWasapiAudioSinkAdapter.forHost()
            )
        }
    }

    /**
     * Ensambla el servicio de aplicación hexagonal completo listo para conectarse al panel gráfico de escritorio.
     */
    fun createDesktopService(
        platform: DesktopOsPlatform = detectHostPlatform(),
        rustCore: RustCoreBridgeContract = NativeCoreLoader.loadOrDummy(),
        preferences: DesktopPreferencesOutboundPort = FileDesktopPreferencesAdapter(),
        linkModeOverride: DesktopLinkMode? = DesktopLinkMode.environmentOverride()
    ): DesktopConnectionService {
        val linkMode = DesktopLinkMode.resolve(linkModeOverride, preferences.isRealLinkPreferred())
        val bundle = createPortBundle(platform, linkMode)
        val (transportPort, phoneEventPort) = createLinkPorts(linkMode, rustCore)
        return DesktopConnectionService(
            aggregate = DesktopSessionAggregate(),
            rustCore = rustCore,
            transportPort = transportPort,
            notificationPort = bundle.notificationPort,
            clipboardPort = bundle.clipboardPort,
            virtualDisplayPort = bundle.virtualDisplayPort,
            virtualCameraMicPort = bundle.virtualCameraMicPort,
            audioSinkPort = bundle.audioSinkPort,
            phoneEventPort = phoneEventPort,
            linkMode = LinkModeInfo(
                label = linkMode.displayLabel,
                realActive = linkMode == DesktopLinkMode.REAL_TCP,
                lockedByEnvironment = linkModeOverride != null
            ),
            preferencesPort = preferences
        )
    }

    /**
     * Construye el par transporte / flujo de eventos del teléfono según el modo de enlace elegido.
     */
    fun createLinkPorts(
        linkMode: DesktopLinkMode,
        rustCore: RustCoreBridgeContract
    ): Pair<DeviceTransportOutboundPort, PhoneEventStreamOutboundPort> = when (linkMode) {
        // [DECISIÓN TÉCNICA]: El simulador sigue siendo el modo por omisión hasta validar el enlace real con dispositivos físicos.
        DesktopLinkMode.SIMULATED -> MultiChannelTransportAdapter(rustCore) to DummyPhoneEventStreamAdapter()
        DesktopLinkMode.REAL_TCP -> {
            val session = TcpLinkSession(rustCore)
            TcpLinkTransportAdapter(session, AdbDeviceBridge(SystemProcessRunner())) to SocketPhoneEventStreamAdapter(session)
        }
    }
}
