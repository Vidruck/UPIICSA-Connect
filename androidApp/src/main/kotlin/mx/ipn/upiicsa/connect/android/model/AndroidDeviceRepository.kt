package mx.ipn.upiicsa.connect.android.model

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import mx.ipn.upiicsa.connect.shared.model.ConnectionDirection
import mx.ipn.upiicsa.connect.shared.model.LinkTelemetry
import mx.ipn.upiicsa.connect.shared.model.RemoteDeviceDescriptor
import mx.ipn.upiicsa.connect.shared.model.TransportType
import mx.ipn.upiicsa.connect.shared.protocol.ChannelMessage
import mx.ipn.upiicsa.connect.shared.protocol.ChannelMessageCodec
import mx.ipn.upiicsa.connect.shared.protocol.ContentOrigin
import mx.ipn.upiicsa.connect.shared.protocol.RustCoreBridgeContract
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Repositorio de la capa Model (MVVM) encargado del enlace móvil <-> ordenador, telemetría y portapapeles.
 */
class AndroidDeviceRepository(
    private val rustCore: RustCoreBridgeContract
) {
    private var linkedDesktop: RemoteDeviceDescriptor? = null
    private var activeTransport: TransportType? = null
    private var localClipboardBuffer: String = ""
    private var sequenceId: Long = 1L

    /**
     * Explora equipos de escritorio Windows o GNU/Linux disponibles dando prioridad a cable USB sobre Wi-Fi y Bluetooth.
     */
    suspend fun scanAvailableDesktops(): List<RemoteDeviceDescriptor> {
        // [DUMMY-PRUEBAS]: Retardo artificial que simula el barrido de UsbManager, NsdManager y BluetoothAdapter.
        delay(400)
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
        ).sortedBy { it.preferredTransport.priorityRank }
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

        // [DUMMY-PRUEBAS]: Serializa el saludo de enlace con el core sin abrir sockets ADB/TLS/RFCOMM reales.
        val hello = ChannelMessage.HandshakeHello(direction, "UPIICSA-Connect Android", pairingPin = 0)
        rustCore.encodeFrame(chosen, hello.channel, sequenceId++, ChannelMessageCodec.encode(hello))
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
     * Sincroniza contenido de texto hacia el portapapeles de Android y lo serializa para enviarlo al ordenador.
     */
    suspend fun syncClipboardFromMobile(text: String): String {
        // [DUMMY-PRUEBAS]: Almacena texto en memoria simulando android.content.ClipboardManager.
        localClipboardBuffer = text
        val transport = activeTransport
        if (transport != null) {
            val message = ChannelMessage.ClipboardText(ContentOrigin.MOBILE, text)
            rustCore.encodeFrame(transport, message.channel, sequenceId++, ChannelMessageCodec.encode(message))
        }
        return localClipboardBuffer
    }

    /**
     * Flujo periódico de telemetría del enlace (latencia y calidad) característico del medio activo.
     */
    fun linkTelemetryStream(transport: TransportType): Flow<LinkTelemetry> = flow {
        var tick = 0L
        var frames = 0L
        while (true) {
            // [DUMMY-PRUEBAS]: Latencia y calidad sintéticas por medio en lugar de medir latidos reales del transporte.
            val jitter = (abs(sin(tick * 0.37)) * 6).roundToInt()
            val (latency, quality) = when (transport) {
                TransportType.USB_CABLE -> (2 + jitter / 3) to 100
                TransportType.WIFI_LAN -> (14 + jitter) to (88 - jitter)
                TransportType.BLUETOOTH -> (42 + jitter * 3) to (71 - jitter)
            }
            frames += 8
            emit(LinkTelemetry(latencyMs = latency, signalQualityPercent = quality, framesSent = frames, framesReceived = frames - 2))
            tick++
            delay(1_000)
        }
    }

    /**
     * Flujo de textos copiados en el ordenador que llegan al teléfono.
     */
    fun clipboardFromDesktopStream(): Flow<String> = flow {
        // [DUMMY-PRUEBAS]: Textos simulados enviados desde el portapapeles del ordenador.
        val samples = listOf("git pull origin dev", "https://www.upiicsa.ipn.mx/", "Reunión de equipo 17:00, sala de cómputo")
        var index = 0
        while (true) {
            delay(30_000)
            emit(samples[index % samples.size])
            index++
        }
    }
}
