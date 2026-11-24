package mx.ipn.upiicsa.connect.desktop.adapters.transport

import kotlinx.coroutines.delay
import mx.ipn.upiicsa.connect.desktop.ports.outbound.DeviceTransportOutboundPort
import mx.ipn.upiicsa.connect.shared.model.ConnectionDirection
import mx.ipn.upiicsa.connect.shared.model.RemoteDeviceDescriptor
import mx.ipn.upiicsa.connect.shared.model.TransportType
import mx.ipn.upiicsa.connect.shared.protocol.FrameValidation
import mx.ipn.upiicsa.connect.shared.protocol.RustCoreBridgeContract
import mx.ipn.upiicsa.connect.shared.protocol.ServiceChannel

/**
 * Adaptador de transporte que negocia canales físicos priorizando cable USB sobre Wi-Fi LAN y Bluetooth RFCOMM.
 */
class MultiChannelTransportAdapter(
    private val rustCore: RustCoreBridgeContract,
    private val discoveryDelayMs: Long = 450L
) : DeviceTransportOutboundPort {

    private var activeDevice: RemoteDeviceDescriptor? = null
    private var activeTransport: TransportType? = null
    private var sequenceCounter: Long = 1L

    /**
     * Cantidad de bytes serializados enviados en la sesión actual (telemetría de diagnóstico).
     */
    var bytesSent: Long = 0L
        private set

    /**
     * Descubre dispositivos Android compatibles ordenando primero los accesibles por cable USB.
     */
    override suspend fun discoverDevices(): List<RemoteDeviceDescriptor> {
        // [DUMMY-PRUEBAS]: Retardo artificial que simula el barrido de adb devices, mDNS y Bluetooth para mostrar el estado "Buscando".
        delay(discoveryDelayMs)
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
        sequenceCounter = 1L
        bytesSent = 0L
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
     * Serializa la carga útil en una trama del core Rust (cabecera + CRC32) y la despacha por el medio activo.
     */
    override suspend fun sendChannelPayload(channel: ServiceChannel, payload: ByteArray): Boolean {
        val transport = activeTransport ?: return false
        if (activeDevice == null) {
            return false
        }
        val frame = rustCore.encodeFrame(transport, channel, sequenceCounter++, payload)
        // [DUMMY-PRUEBAS]: Validación en bucle local (loopback) de la trama serializada en lugar de escribirla al socket/endpoint USB.
        val accepted = rustCore.validateWireFrame(frame) == FrameValidation.Valid(channel)
        if (accepted) {
            bytesSent += frame.size
        }
        return accepted
    }
}
