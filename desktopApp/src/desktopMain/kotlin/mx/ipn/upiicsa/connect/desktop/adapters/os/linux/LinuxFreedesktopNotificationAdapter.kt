package mx.ipn.upiicsa.connect.desktop.adapters.os.linux

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mx.ipn.upiicsa.connect.desktop.adapters.process.ExecutableLocator
import mx.ipn.upiicsa.connect.desktop.adapters.process.ProcessRunner
import mx.ipn.upiicsa.connect.desktop.adapters.process.SystemProcessRunner
import mx.ipn.upiicsa.connect.desktop.ports.outbound.OsNotificationOutboundPort
import mx.ipn.upiicsa.connect.shared.model.NotificationResolutionAction
import mx.ipn.upiicsa.connect.shared.model.PhoneNotificationItem
import java.util.concurrent.ConcurrentHashMap

/**
 * Adaptador exclusivo de GNU/Linux para publicar y retirar notificaciones mediante org.freedesktop.Notifications (D-Bus)
 * con la utilidad `gdbus` de GLib. Sin `gdbus` ni bus de sesión, registra las notificaciones solo en memoria.
 */
class LinuxFreedesktopNotificationAdapter(
    private val runner: ProcessRunner? = null
) : OsNotificationOutboundPort {

    private val desktopIds = ConcurrentHashMap<String, Long>()
    private val memoryLog = mutableListOf<String>()

    /**
     * Mecanismo efectivo con el que se muestran las notificaciones.
     */
    val backendLabel: String
        get() = if (runner != null) "D-Bus org.freedesktop.Notifications (gdbus)" else "Memoria (sin notificaciones del sistema)"

    /**
     * Muestra la notificación del teléfono; si ya existe una con el mismo identificador, la reemplaza.
     */
    override suspend fun publishPhoneNotification(item: PhoneNotificationItem) {
        if (runner == null) {
            // [DECISIÓN TÉCNICA]: Sin gdbus o sin bus de sesión (servidores, pruebas) la notificación queda solo en el panel de la app.
            synchronized(memoryLog) { memoryLog += "${item.appName} - ${item.senderTitle}: ${item.messageBody}" }
            return
        }
        val command = FreedesktopNotifyCommands.notify(
            summary = "${item.appName}: ${item.senderTitle}",
            body = item.messageBody,
            replacesId = desktopIds[item.notificationId] ?: 0L,
            expireTimeoutMs = EXPIRE_TIMEOUT_MS
        )
        val result = withContext(Dispatchers.IO) { runner.run(command, null, TIMEOUT_MS) }
        val desktopId = result?.takeIf { it.succeeded }?.let { FreedesktopNotifyCommands.parseNotifyReply(it.stdout) }
        if (desktopId != null) {
            desktopIds[item.notificationId] = desktopId
        }
    }

    /**
     * Retira del escritorio la notificación ya resuelta desde la PC (respuesta, leída o descartada).
     */
    override suspend fun dispatchResolutionFeedback(action: NotificationResolutionAction) {
        val notificationId = when (action) {
            is NotificationResolutionAction.QuickReply -> action.notificationId
            is NotificationResolutionAction.MarkAsRead -> action.notificationId
            is NotificationResolutionAction.Dismiss -> action.notificationId
        }
        val desktopId = desktopIds.remove(notificationId) ?: return
        val activeRunner = runner ?: return
        withContext(Dispatchers.IO) { activeRunner.run(FreedesktopNotifyCommands.close(desktopId), null, TIMEOUT_MS) }
    }

    companion object {
        private const val TIMEOUT_MS = 3_000L
        private const val EXPIRE_TIMEOUT_MS = 8_000

        /**
         * Construye el adaptador con `gdbus` si existe la utilidad y un bus de sesión.
         */
        fun forHost(
            runner: ProcessRunner = SystemProcessRunner(),
            environment: Map<String, String> = System.getenv()
        ): LinuxFreedesktopNotificationAdapter {
            val sessionBus = !environment["DBUS_SESSION_BUS_ADDRESS"].isNullOrBlank() ||
                !environment["XDG_RUNTIME_DIR"].isNullOrBlank()
            val available = sessionBus && ExecutableLocator.isOnPath("gdbus", environment["PATH"])
            return LinuxFreedesktopNotificationAdapter(if (available) runner else null)
        }
    }
}
