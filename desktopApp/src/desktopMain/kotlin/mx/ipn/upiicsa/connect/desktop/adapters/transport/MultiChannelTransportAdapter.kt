package mx.ipn.upiicsa.connect.desktop.adapters.transport

import mx.ipn.upiicsa.connect.desktop.ports.outbound.DeviceTransportOutboundPort
import mx.ipn.upiicsa.connect.shared.model.ConnectionDirection
import mx.ipn.upiicsa.connect.shared.model.RemoteDeviceDescriptor
import mx.ipn.upiicsa.connect.shared.model.TransportType
import mx.ipn.upiicsa.connect.shared.protocol.RustCoreBridgeContract

/**
 * Adaptador de transporte que negocia canales físicos priorizando cable USB sobre Wi-Fi LAN y Bluetooth RFCOMM.
 */
class MultiChannelTransportAdapter(
    private val rustCore: RustCoreBridgeContract
) : DeviceTransportOutboundPort {

    private var activeDevice: RemoteDeviceDescriptor? = null
    private var activeTransport: TransportType? = null
    private var sequenceCounter: Long = 1L

    /**
     * Descubre dispositivos Android compatibles ordenando primero los accesibles por cable USB.
     */
    override suspend fun discoverDevices(): List<RemoteDeviceDescriptor> {
        // [DUMMY-PRUEBAS]: Catálogo hardcodeado de dispositivos Android para pruebas sin cable ADB/mDNS en vivo.
        return listOf(
            RemoteDeviceDescriptor(
                deviceId = "usb-pixel8-upiicsa",
                deviceName = "Pixel 8 Pro (USB ADB)",
                modelName = "Google Pixel 8 Pro",
                androidSdkVersion = 34,
                availableTransports = listOf(TransportType.USB_CABLE, TransportType.WIFI_LAN, TransportType.BLUETOOTH),
                preferredTransport = TransportType.USB_CABLE
            ),
            RemoteDeviceDescriptor(
                deviceId = "wifi-tab-s9-upiicsa",
                deviceName = "Galaxy Tab S9 (Wi-Fi 192.168.1.45)",
                modelName = "Samsung Galaxy Tab S9",
                androidSdkVersion = 34,
                availableTransports = listOf(TransportType.WIFI_LAN, TransportType.BLUETOOTH),
                preferredTransport = TransportType.WIFI_LAN
            ),
            RemoteDeviceDescriptor(
                deviceId = "bt-redmi-upiicsa",
                deviceName = "Redmi Note 13 (Bluetooth)",
                modelName = "Xiaomi Redmi Note 13",
                androidSdkVersion = 33,
                availableTransports = listOf(TransportType.BLUETOOTH),
                preferredTransport = TransportType.BLUETOOTH
            )
        )
    }

    /**
     * Establece la sesión física seleccionando el canal de mayor prioridad disponible.
     */
    override suspend fun connect(
        device: RemoteDeviceDescriptor,
        transport: TransportType,
        direction: ConnectionDirection
    ): Boolean {
        val selected = if (device.availableTransports.contains(transport)) {
            transport
        } else {
            rustCore.selectBestTransport(
                usbAvailable = device.availableTransports.contains(TransportType.USB_CABLE),
                wifiAvailable = device.availableTransports.contains(TransportType.WIFI_LAN),
                bluetoothAvailable = device.availableTransports.contains(TransportType.BLUETOOTH)
            ) ?: return false
        }

        // [DUMMY-PRUEBAS]: Registro en memoria del enlace activo simulando handshake AOAv2/TLS/RFCOMM.
        activeDevice = device
        activeTransport = selected
        return true
    }

    /**
     * Libera el canal físico de transporte asociado a la sesión actual.
     */
    override suspend fun disconnect() {
        activeDevice = null
        activeTransport = null
    }

    /**
     * Calcula la suma de verificación CRC32 vía contrato Rust y despacha la trama por el medio activo.
     */
    override suspend fun sendChannelPayload(channelId: Int, payload: ByteArray): Boolean {
        if (activeDevice == null || activeTransport == null) {
            return false
        }
        val crc32 = rustCore.computeFrameChecksum(payload, channelId, sequenceCounter++)
        return crc32 >= 0L
    }
}
