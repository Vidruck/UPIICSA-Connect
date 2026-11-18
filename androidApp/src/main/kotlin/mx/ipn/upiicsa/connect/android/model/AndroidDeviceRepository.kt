package mx.ipn.upiicsa.connect.android.model

import mx.ipn.upiicsa.connect.shared.model.ConnectionDirection
import mx.ipn.upiicsa.connect.shared.model.RemoteDeviceDescriptor
import mx.ipn.upiicsa.connect.shared.model.TransportType
import mx.ipn.upiicsa.connect.shared.protocol.RustCoreBridgeContract

/**
 * Repositorio de la capa Model (MVVM) encargado del enlace móvil <-> ordenador y sincronización de portapapeles.
 */
class AndroidDeviceRepository(
    private val rustCore: RustCoreBridgeContract
) {
    private var linkedDesktop: RemoteDeviceDescriptor? = null
    private var activeTransport: TransportType? = null
    private var localClipboardBuffer: String = ""

    /**
     * Explora equipos de escritorio Windows o GNU/Linux disponibles dando prioridad a cable USB sobre Wi-Fi y Bluetooth.
     */
    suspend fun scanAvailableDesktops(): List<RemoteDeviceDescriptor> {
        // [DUMMY-PRUEBAS]: Listado simulado de equipos de escritorio hasta integrar UsbManager (AOAv2), NsdManager y BluetoothAdapter.
        return listOf(
            RemoteDeviceDescriptor(
                deviceId = "pc-linux-upiicsa",
                deviceName = "Laptop-UPIICSA-Linux (Cable USB)",
                modelName = "GNU/Linux Workstation",
                androidSdkVersion = null,
                availableTransports = listOf(TransportType.USB_CABLE, TransportType.WIFI_LAN, TransportType.BLUETOOTH),
                preferredTransport = TransportType.USB_CABLE
            ),
            RemoteDeviceDescriptor(
                deviceId = "pc-win11-upiicsa",
                deviceName = "Workstation-Win11 (Wi-Fi / BT)",
                modelName = "Windows 11 Pro",
                androidSdkVersion = null,
                availableTransports = listOf(TransportType.WIFI_LAN, TransportType.BLUETOOTH),
                preferredTransport = TransportType.WIFI_LAN
            )
        )
    }

    /**
     * Inicia o acepta la conexión hacia un ordenador resolviendo el medio óptimo mediante el contrato Rust.
     */
    suspend fun connectToDesktop(
        desktop: RemoteDeviceDescriptor,
        requestedTransport: TransportType?,
        direction: ConnectionDirection
    ): TransportType {
        val chosen = if (requestedTransport != null && desktop.availableTransports.contains(requestedTransport)) {
            requestedTransport
        } else {
            rustCore.selectBestTransport(
                usbAvailable = desktop.availableTransports.contains(TransportType.USB_CABLE),
                wifiAvailable = desktop.availableTransports.contains(TransportType.WIFI_LAN),
                bluetoothAvailable = desktop.availableTransports.contains(TransportType.BLUETOOTH)
            ) ?: TransportType.USB_CABLE
        }

        // [DUMMY-PRUEBAS]: Guarda estado del enlace móvil->PC sin abrir sockets ADB/TLS/RFCOMM reales.
        val handshakePayload = "CONNECT|${direction.name}|${chosen.name}".encodeToByteArray()
        rustCore.computeFrameChecksum(handshakePayload, channelId = 1, sequenceId = 1L)
        linkedDesktop = desktop
        activeTransport = chosen
        return chosen
    }

    /**
     * Finaliza el enlace activo entre el teléfono Android y el equipo de escritorio.
     */
    suspend fun disconnectDesktop() {
        linkedDesktop = null
        activeTransport = null
    }

    /**
     * Sincroniza contenido de texto hacia el portapapeles de Android y lo prepara para envío al ordenador.
     */
    suspend fun syncClipboardFromMobile(text: String): String {
        // [DUMMY-PRUEBAS]: Almacena texto en memoria simulando android.content.ClipboardManager.
        localClipboardBuffer = text
        return localClipboardBuffer
    }
}
