package mx.ipn.upiicsa.connect.desktop.adapters.transport.tcp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mx.ipn.upiicsa.connect.desktop.ports.outbound.DeviceTransportOutboundPort
import mx.ipn.upiicsa.connect.shared.model.ConnectionDirection
import mx.ipn.upiicsa.connect.shared.model.RemoteDeviceDescriptor
import mx.ipn.upiicsa.connect.shared.model.TransportType
import mx.ipn.upiicsa.connect.shared.protocol.ServiceChannel
import java.io.IOException

/**
 * Transporte real por TCP: descubre teléfonos con `adb`, crea el túnel `adb reverse` y espera a que la app Android se conecte.
 */
class TcpLinkTransportAdapter(
    private val session: TcpLinkSession,
    private val adb: AdbDeviceBridge,
    private val acceptTimeoutMs: Int = DEFAULT_ACCEPT_TIMEOUT_MS
) : DeviceTransportOutboundPort {

    private var reversedSerial: String? = null
    private var reversedPort: Int = 0

    /**
     * Lista los teléfonos conectados por cable con depuración USB autorizada.
     */
    override suspend fun discoverDevices(): List<RemoteDeviceDescriptor> = withContext(Dispatchers.IO) {
        adb.listDevices().map { device ->
            RemoteDeviceDescriptor(
                deviceId = ADB_ID_PREFIX + device.serial,
                deviceName = "${device.model} (USB ADB)",
                modelName = device.model,
                androidSdkVersion = null,
                availableTransports = listOf(TransportType.USB_CABLE),
                preferredTransport = TransportType.USB_CABLE
            )
        }
    }

    /**
     * Abre el servidor, crea el túnel USB para dispositivos ADB y espera la conexión de la app Android.
     */
    override suspend fun connect(
        device: RemoteDeviceDescriptor,
        transport: TransportType,
        direction: ConnectionDirection
    ): Boolean = withContext(Dispatchers.IO) {
        releaseTunnel()
        val port = session.listen()
        val serial = device.deviceId.takeIf { it.startsWith(ADB_ID_PREFIX) }?.removePrefix(ADB_ID_PREFIX)
        if (serial != null) {
            if (!adb.reverse(serial, port)) {
                return@withContext false
            }
            reversedSerial = serial
            reversedPort = port
        }
        val channel = session.awaitPhone(TransportType.USB_CABLE, acceptTimeoutMs)
        if (channel == null) {
            releaseTunnel()
        }
        channel != null
    }

    /**
     * Cierra el canal con el teléfono y elimina el túnel USB.
     */
    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        session.closeChannel()
        releaseTunnel()
    }

    /**
     * Envía la carga útil como trama del core por el socket activo; `false` si no hay teléfono conectado o falla la escritura.
     */
    override suspend fun sendChannelPayload(channel: ServiceChannel, payload: ByteArray): Boolean {
        val link = session.activeChannel() ?: return false
        return try {
            link.sendRaw(channel, payload)
            true
        } catch (error: IOException) {
            session.closeChannel()
            false
        }
    }

    /**
     * Elimina el túnel `adb reverse` vigente, si existe.
     */
    private fun releaseTunnel() {
        val serial = reversedSerial ?: return
        adb.removeReverse(serial, reversedPort)
        reversedSerial = null
    }

    companion object {
        /**
         * Prefijo de los identificadores de dispositivos descubiertos con `adb`.
         */
        const val ADB_ID_PREFIX = "adb:"

        private const val DEFAULT_ACCEPT_TIMEOUT_MS = 30_000
    }
}
