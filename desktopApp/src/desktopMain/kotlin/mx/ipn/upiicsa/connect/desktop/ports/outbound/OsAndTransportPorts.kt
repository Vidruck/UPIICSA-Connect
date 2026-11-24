package mx.ipn.upiicsa.connect.desktop.ports.outbound

import kotlinx.coroutines.flow.Flow
import mx.ipn.upiicsa.connect.desktop.domain.PhoneEvent
import mx.ipn.upiicsa.connect.shared.model.ConnectionDirection
import mx.ipn.upiicsa.connect.shared.model.NotificationResolutionAction
import mx.ipn.upiicsa.connect.shared.model.PhoneNotificationItem
import mx.ipn.upiicsa.connect.shared.model.RemoteDeviceDescriptor
import mx.ipn.upiicsa.connect.shared.model.SecondaryDisplayConfig
import mx.ipn.upiicsa.connect.shared.model.TransportType
import mx.ipn.upiicsa.connect.shared.protocol.ServiceChannel

/**
 * Puerto de salida de transporte físico que oculta los detalles de USB, Wi-Fi LAN y Bluetooth RFCOMM.
 */
interface DeviceTransportOutboundPort {

    /**
     * Lista dispositivos detectados por cable USB (prioridad 1) y redes inalámbricas (prioridad 2).
     */
    suspend fun discoverDevices(): List<RemoteDeviceDescriptor>

    /**
     * Abre el canal lógico de comunicación hacia el dispositivo seleccionado.
     */
    suspend fun connect(
        device: RemoteDeviceDescriptor,
        transport: TransportType,
        direction: ConnectionDirection
    ): Boolean

    /**
     * Cierra el canal físico activo.
     */
    suspend fun disconnect()

    /**
     * Envía una trama etiquetada por el canal lógico indicado hacia Android.
     */
    suspend fun sendChannelPayload(channel: ServiceChannel, payload: ByteArray): Boolean
}

/**
 * Puerto de salida exclusivo para notificaciones nativas del sistema operativo de escritorio.
 */
interface OsNotificationOutboundPort {

    /**
     * Muestra en el sistema anfitrión una notificación espejada proveniente del teléfono.
     */
    suspend fun publishPhoneNotification(item: PhoneNotificationItem)

    /**
     * Notifica al adaptador del sistema operativo la resolución aplicada por el usuario desde la PC.
     */
    suspend fun dispatchResolutionFeedback(action: NotificationResolutionAction)
}

/**
 * Puerto de salida para las preferencias persistentes del escritorio.
 */
interface DesktopPreferencesOutboundPort {

    /**
     * Indica si el usuario eligió iniciar con el enlace real por cable USB.
     */
    fun isRealLinkPreferred(): Boolean

    /**
     * Guarda la elección del modo de enlace; retorna `false` si no se pudo persistir.
     */
    fun setRealLinkPreferred(enabled: Boolean): Boolean
}

/**
 * Puerto de salida exclusivo para el portapapeles del sistema operativo de escritorio.
 */
interface OsClipboardOutboundPort {

    /**
     * Escribe texto en el portapapeles nativo del sistema operativo.
     */
    suspend fun writeClipboardText(text: String)

    /**
     * Lee el texto actual almacenado en el portapapeles nativo del sistema operativo.
     */
    suspend fun readClipboardText(): String
}

/**
 * Puerto de salida exclusivo para la gestión del monitor virtual secundario en el sistema operativo.
 */
interface OsVirtualDisplayOutboundPort {

    /**
     * Crea o habilita un monitor virtual secundario con la resolución y tasa indicadas.
     */
    suspend fun startVirtualDisplay(config: SecondaryDisplayConfig): Boolean

    /**
     * Libera el monitor virtual secundario del sistema operativo.
     */
    suspend fun stopVirtualDisplay()

    /**
     * Captura un cuadro codificado del escritorio virtual para enviarlo a Android.
     */
    suspend fun captureCurrentFrame(): ByteArray
}

/**
 * Puerto de salida exclusivo para exponer la cámara y micrófono del móvil como dispositivos virtuales en el portátil.
 */
interface OsVirtualCameraMicOutboundPort {

    /**
     * Activa los dispositivos virtuales de webcam y entrada de micrófono en el sistema operativo.
     */
    suspend fun enableVirtualDevices(enableCamera: Boolean, enableMicrophone: Boolean): Boolean

    /**
     * Inyecta un cuadro de video proveniente de la cámara del teléfono en el dispositivo virtual del PC.
     */
    suspend fun pushVideoFrame(frameBytes: ByteArray)

    /**
     * Inyecta muestras de audio del micrófono del teléfono hacia la entrada virtual de videollamadas.
     */
    suspend fun pushMicAudio(pcmSamples: ShortArray)
}

/**
 * Puerto de salida exclusivo para reproducir el flujo de audio de Music Mode en la tarjeta de sonido del ordenador.
 */
interface OsAudioSinkOutboundPort {

    /**
     * Abre el sumidero de audio nativo de baja latencia a la frecuencia especificada.
     */
    suspend fun openMusicModeSink(sampleRateHz: Int): Boolean

    /**
     * Envía muestras PCM procesadas por el core DSP directamente hacia la tarjeta de sonido del PC.
     */
    suspend fun writePcmSamples(samples: ShortArray)

    /**
     * Cierra el sumidero de audio al desactivar Music Mode.
     */
    suspend fun closeMusicModeSink()
}

/**
 * Puerto de salida que entrega como flujo los eventos producidos por el teléfono conectado (notificaciones, audio, video, latidos).
 */
interface PhoneEventStreamOutboundPort {

    /**
     * Flujo frío de eventos del dispositivo indicado sobre el medio activo; se cancela al cerrar la sesión.
     */
    fun events(device: RemoteDeviceDescriptor, transport: TransportType): Flow<PhoneEvent>
}
