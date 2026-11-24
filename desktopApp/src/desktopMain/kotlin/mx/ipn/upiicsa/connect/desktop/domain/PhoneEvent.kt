package mx.ipn.upiicsa.connect.desktop.domain

import mx.ipn.upiicsa.connect.shared.model.PhoneNotificationItem

/**
 * Eventos entrantes producidos por el teléfono Android conectado y recibidos por el escritorio a través del transporte.
 */
sealed class PhoneEvent {

    /**
     * Nueva notificación capturada en el teléfono para espejarse en el ordenador.
     */
    data class IncomingNotification(val item: PhoneNotificationItem) : PhoneEvent()

    /**
     * Texto copiado en el teléfono que debe aplicarse al portapapeles del ordenador.
     */
    data class ClipboardFromPhone(val text: String) : PhoneEvent()

    /**
     * Bloque PCM de Music Mode capturado en el teléfono junto con el título de la pista en reproducción.
     */
    class MusicPcmBlock(val sequenceId: Long, val samples: ShortArray, val trackTitle: String) : PhoneEvent()

    /**
     * Bloque PCM del micrófono del teléfono para la entrada virtual de videollamadas.
     */
    class MicrophonePcmBlock(val samples: ShortArray, val level: Float) : PhoneEvent()

    /**
     * Cuadro de video codificado de la cámara del teléfono para la webcam virtual.
     */
    class CameraFrame(val frameIndex: Long, val encodedFrame: ByteArray) : PhoneEvent()

    /**
     * Latido periódico con métricas de calidad del enlace medidas por el teléfono.
     */
    data class LinkHeartbeat(val latencyMs: Int, val signalQualityPercent: Int) : PhoneEvent()

    /**
     * Señal de sincronía vertical del monitor secundario: el dispositivo está listo para recibir el siguiente cuadro.
     */
    data class DisplayVsync(val frameIndex: Long) : PhoneEvent()

    /**
     * El enlace físico con el teléfono se cerró (desconexión del par o latidos vencidos); la sesión debe finalizar.
     */
    data class LinkClosed(val reason: String) : PhoneEvent()
}
