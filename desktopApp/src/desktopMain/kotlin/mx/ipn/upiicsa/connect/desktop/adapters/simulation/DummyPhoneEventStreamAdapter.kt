package mx.ipn.upiicsa.connect.desktop.adapters.simulation

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import mx.ipn.upiicsa.connect.desktop.domain.PhoneEvent
import mx.ipn.upiicsa.connect.desktop.ports.outbound.PhoneEventStreamOutboundPort
import mx.ipn.upiicsa.connect.shared.model.PhoneNotificationItem
import mx.ipn.upiicsa.connect.shared.model.RemoteDeviceDescriptor
import mx.ipn.upiicsa.connect.shared.model.TransportType
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Simulador del teléfono conectado: produce notificaciones, portapapeles, audio, micrófono, cámara y latidos sintéticos.
 */
class DummyPhoneEventStreamAdapter(
    private val tickMillis: Long = 120L
) : PhoneEventStreamOutboundPort {

    private val clockFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * Emite primero la bandeja pendiente del teléfono y después eventos periódicos mientras dure la sesión.
     */
    override fun events(device: RemoteDeviceDescriptor, transport: TransportType): Flow<PhoneEvent> = flow {
        // [DUMMY-PRUEBAS]: Bandeja inicial de notificaciones simuladas en lugar del NotificationListenerService del teléfono real.
        for (item in backlogNotifications()) {
            emit(PhoneEvent.IncomingNotification(item))
        }
        var tick = 0L
        while (true) {
            delay(tickMillis)
            tick++
            // [DUMMY-PRUEBAS]: Bloques PCM, micrófono, cámara y vsync sintéticos generados a ritmo fijo sin hardware Android.
            emit(PhoneEvent.MusicPcmBlock(tick, synthesizeMusicBlock(tick), trackTitleFor(tick)))
            val micLevel = microphoneLevelFor(tick)
            emit(PhoneEvent.MicrophonePcmBlock(synthesizeMicBlock(micLevel), micLevel))
            emit(PhoneEvent.CameraFrame(tick, syntheticCameraFrame(tick)))
            emit(PhoneEvent.DisplayVsync(tick))
            if (tick % HEARTBEAT_EVERY_TICKS == 0L) {
                emit(PhoneEvent.LinkHeartbeat(latencyFor(transport, tick), qualityFor(transport, tick)))
            }
            if (tick % NOTIFICATION_EVERY_TICKS == 0L) {
                emit(PhoneEvent.IncomingNotification(rotatingNotification(tick)))
            }
            if (tick % CLIPBOARD_EVERY_TICKS == CLIPBOARD_EVERY_TICKS / 2) {
                emit(PhoneEvent.ClipboardFromPhone(rotatingClipboard(tick)))
            }
        }
    }

    /**
     * Notificaciones pendientes al momento de enlazar.
     */
    private fun backlogNotifications(): List<PhoneNotificationItem> {
        // [DUMMY-PRUEBAS]: Datos hardcodeados para probar respuesta remota y resolución en escritorio sin teléfono en vivo.
        return listOf(
            PhoneNotificationItem(
                notificationId = "notif-1",
                appName = "WhatsApp",
                packageName = "com.whatsapp",
                senderTitle = "Equipo Proyecto Móvil",
                messageBody = "¿Ya quedó lista la arquitectura Ports & Adapters para Linux y Windows?",
                timestampLabel = "14:28",
                supportsInlineReply = true
            ),
            PhoneNotificationItem(
                notificationId = "notif-2",
                appName = "Classroom IPN",
                packageName = "com.google.android.apps.classroom",
                senderTitle = "Programación Móvil - 7mo",
                messageBody = "Recordatorio: entrega de avance de tercer parcial.",
                timestampLabel = "14:15",
                supportsInlineReply = false
            ),
            PhoneNotificationItem(
                notificationId = "notif-3",
                appName = "Telegram",
                packageName = "org.telegram.messenger",
                senderTitle = "Laboratorio de Redes",
                messageBody = "La práctica de sockets se recorre al jueves a las 10:00.",
                timestampLabel = "13:52",
                supportsInlineReply = true
            )
        )
    }

    /**
     * Notificación periódica rotativa con identificador único por instante.
     */
    private fun rotatingNotification(tick: Long): PhoneNotificationItem {
        val catalog = listOf(
            Triple("WhatsApp", "com.whatsapp", "Mamá" to "¿Llegas a comer o te guardo?"),
            Triple("Gmail", "com.google.android.gm", "Servicio Escolar UPIICSA" to "Tu constancia de estudios está lista."),
            Triple("Telegram", "org.telegram.messenger", "Club de Robótica" to "Reunión hoy en el edificio de Ingeniería."),
            Triple("Outlook", "com.microsoft.office.outlook", "Academia de Computación" to "Acta de evaluación disponible.")
        )
        val (app, pkg, content) = catalog[((tick / NOTIFICATION_EVERY_TICKS) % catalog.size).toInt()]
        return PhoneNotificationItem(
            notificationId = "dummy-$tick",
            appName = app,
            packageName = pkg,
            senderTitle = content.first,
            messageBody = content.second,
            timestampLabel = LocalTime.now().format(clockFormatter),
            supportsInlineReply = app != "Gmail" && app != "Outlook"
        )
    }

    /**
     * Texto rotativo copiado en el teléfono.
     */
    private fun rotatingClipboard(tick: Long): String {
        val samples = listOf(
            "https://www.upiicsa.ipn.mx/",
            "Salón 312, Edificio de Ingeniería",
            "Código de verificación: 482913"
        )
        return samples[((tick / CLIPBOARD_EVERY_TICKS) % samples.size).toInt()]
    }

    /**
     * Genera un bloque PCM estéreo intercalado con envolvente variable que simula música.
     */
    private fun synthesizeMusicBlock(tick: Long): ShortArray {
        val envelope = 0.25 + 0.6 * abs(sin(tick * 0.21)) * (0.65 + 0.35 * sin(tick * 0.047))
        val amplitude = envelope * Short.MAX_VALUE * 0.8
        return ShortArray(SAMPLES_PER_BLOCK) { index ->
            val phase = 2 * PI * 440.0 * (tick * SAMPLES_PER_BLOCK + index) / 48_000.0
            (amplitude * sin(phase)).roundToInt().toShort()
        }
    }

    /**
     * Genera un bloque PCM monoaural de micrófono con la amplitud indicada.
     */
    private fun synthesizeMicBlock(level: Float): ShortArray = ShortArray(MIC_SAMPLES_PER_BLOCK) { index ->
        (level * Short.MAX_VALUE * 0.7 * sin(index * 0.3)).roundToInt().toShort()
    }

    /**
     * Nivel de voz simulado con pausas periódicas.
     */
    private fun microphoneLevelFor(tick: Long): Float {
        val speaking = (tick / 20) % 3 != 2L
        return if (speaking) (0.25 + 0.45 * abs(sin(tick * 0.6))).toFloat() else 0.03f
    }

    /**
     * Cuadro de cámara sintético con cabecera "CAMF" y contador.
     */
    private fun syntheticCameraFrame(tick: Long): ByteArray =
        "CAMF".encodeToByteArray() + ByteArray(8) { index -> (tick shr (56 - index * 8)).toByte() }

    /**
     * Título de pista simulada según el avance del flujo.
     */
    private fun trackTitleFor(tick: Long): String {
        val tracks = listOf("Himno del IPN (Orquesta)", "Lo-fi para estudiar", "Podcast: Ingeniería en Informática")
        return tracks[((tick / 400) % tracks.size).toInt()]
    }

    /**
     * Latencia simulada característica de cada medio físico.
     */
    private fun latencyFor(transport: TransportType, tick: Long): Int {
        val jitter = abs(sin(tick * 0.37)).let { (it * 6).roundToInt() }
        return when (transport) {
            TransportType.USB_CABLE -> 2 + jitter / 3
            TransportType.WIFI_LAN -> 14 + jitter
            TransportType.BLUETOOTH -> 42 + jitter * 3
        }
    }

    /**
     * Calidad de señal simulada característica de cada medio físico.
     */
    private fun qualityFor(transport: TransportType, tick: Long): Int {
        val variation = (abs(sin(tick * 0.11)) * 6).roundToInt()
        return when (transport) {
            TransportType.USB_CABLE -> 100
            TransportType.WIFI_LAN -> 88 - variation
            TransportType.BLUETOOTH -> 71 - variation
        }
    }

    private companion object {
        const val SAMPLES_PER_BLOCK = 960
        const val MIC_SAMPLES_PER_BLOCK = 480
        const val HEARTBEAT_EVERY_TICKS = 8L
        const val NOTIFICATION_EVERY_TICKS = 150L
        const val CLIPBOARD_EVERY_TICKS = 260L
    }
}
