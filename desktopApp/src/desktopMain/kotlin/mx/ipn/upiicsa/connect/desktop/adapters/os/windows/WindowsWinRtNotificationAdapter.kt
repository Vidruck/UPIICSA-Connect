package mx.ipn.upiicsa.connect.desktop.adapters.os.windows

import mx.ipn.upiicsa.connect.desktop.ports.outbound.OsNotificationOutboundPort
import mx.ipn.upiicsa.connect.shared.model.NotificationResolutionAction
import mx.ipn.upiicsa.connect.shared.model.PhoneNotificationItem

/**
 * Adaptador exclusivo de Windows para publicar y resolver notificaciones móviles mediante WinRT Toast API.
 */
class WindowsWinRtNotificationAdapter : OsNotificationOutboundPort {

    private val toastHistory = mutableListOf<String>()

    /**
     * Publica una notificación del teléfono dentro del Centro de Actividades de Windows.
     */
    override suspend fun publishPhoneNotification(item: PhoneNotificationItem) {
        // [DUMMY-PRUEBAS]: Almacena en memoria la notificación en lugar de invocar Windows.UI.Notifications.ToastNotificationManager.
        toastHistory.add("[Windows WinRT] ${item.appName} - ${item.senderTitle}: ${item.messageBody}")
    }

    /**
     * Registra en el adaptador de Windows la resolución enviada por el usuario desde la PC.
     */
    override suspend fun dispatchResolutionFeedback(action: NotificationResolutionAction) {
        // [DUMMY-PRUEBAS]: Simula el cierre o actualización del Toast interactivo en Windows 10/11.
        toastHistory.add("[Windows WinRT Feedback] Acción ejecutada: $action")
    }
}
