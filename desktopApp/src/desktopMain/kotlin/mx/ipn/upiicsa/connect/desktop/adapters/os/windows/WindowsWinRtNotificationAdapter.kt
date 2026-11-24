package mx.ipn.upiicsa.connect.desktop.adapters.os.windows

import mx.ipn.upiicsa.connect.desktop.adapters.process.ProcessRunner
import mx.ipn.upiicsa.connect.desktop.adapters.process.SystemProcessRunner
import mx.ipn.upiicsa.connect.desktop.ports.outbound.OsNotificationOutboundPort
import mx.ipn.upiicsa.connect.shared.model.NotificationResolutionAction
import mx.ipn.upiicsa.connect.shared.model.PhoneNotificationItem
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Adaptador exclusivo de Windows para publicar y retirar notificaciones *toast* de WinRT mediante PowerShell.
 * Sin PowerShell (por ejemplo, en pruebas fuera de Windows) registra las notificaciones solo en memoria.
 */
class WindowsWinRtNotificationAdapter(
    private val runner: ProcessRunner? = null
) : OsNotificationOutboundPort {

    private val memoryLog = mutableListOf<String>()

    // [DECISIÓN TÉCNICA]: PowerShell tarda alrededor de un segundo en arrancar; un único hilo en segundo plano evita frenar el flujo de eventos del teléfono y conserva el orden mostrar/retirar.
    private val worker: ExecutorService by lazy {
        Executors.newSingleThreadExecutor { task -> Thread(task, "upiicsa-toast-winrt").apply { isDaemon = true } }
    }

    /**
     * Mecanismo efectivo con el que se muestran las notificaciones.
     */
    val backendLabel: String
        get() = if (runner != null) "Toast WinRT (PowerShell)" else "Memoria (sin notificaciones del sistema)"

    /**
     * Encola la notificación del teléfono como toast; un toast con la misma etiqueta se reemplaza.
     */
    override suspend fun publishPhoneNotification(item: PhoneNotificationItem) {
        if (runner == null) {
            // [DECISIÓN TÉCNICA]: Fuera de Windows la notificación queda solo en el panel de la app.
            synchronized(memoryLog) { memoryLog += "${item.appName} - ${item.senderTitle}: ${item.messageBody}" }
            return
        }
        val script = WindowsToastScripts.showScript(item.notificationId, "${item.appName}: ${item.senderTitle}", item.messageBody)
        worker.execute { runner.run(WindowsToastScripts.command(script), null, TIMEOUT_MS) }
    }

    /**
     * Encola el retiro del toast de la notificación ya resuelta desde la PC.
     */
    override suspend fun dispatchResolutionFeedback(action: NotificationResolutionAction) {
        val activeRunner = runner ?: return
        val notificationId = when (action) {
            is NotificationResolutionAction.QuickReply -> action.notificationId
            is NotificationResolutionAction.MarkAsRead -> action.notificationId
            is NotificationResolutionAction.Dismiss -> action.notificationId
        }
        val script = WindowsToastScripts.removeScript(notificationId)
        worker.execute { activeRunner.run(WindowsToastScripts.command(script), null, TIMEOUT_MS) }
    }

    companion object {
        private const val TIMEOUT_MS = 10_000L

        /**
         * Construye el adaptador con PowerShell como mecanismo de publicación.
         */
        fun forHost(runner: ProcessRunner = SystemProcessRunner()): WindowsWinRtNotificationAdapter =
            WindowsWinRtNotificationAdapter(runner)
    }
}
