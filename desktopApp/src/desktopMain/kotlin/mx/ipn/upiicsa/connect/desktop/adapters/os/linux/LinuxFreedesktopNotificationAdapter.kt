package mx.ipn.upiicsa.connect.desktop.adapters.os.linux

import mx.ipn.upiicsa.connect.desktop.ports.outbound.OsNotificationOutboundPort
import mx.ipn.upiicsa.connect.shared.model.NotificationResolutionAction
import mx.ipn.upiicsa.connect.shared.model.PhoneNotificationItem

/**
 * Adaptador exclusivo de GNU/Linux para publicar y resolver notificaciones mediante org.freedesktop.Notifications (D-Bus).
 */
class LinuxFreedesktopNotificationAdapter : OsNotificationOutboundPort {

    private val publishedLog = mutableListOf<String>()

    /**
     * Publica una notificación entrante del teléfono en el demonio de notificaciones de GNU/Linux.
     */
    override suspend fun publishPhoneNotification(item: PhoneNotificationItem) {
        // [DUMMY-PRUEBAS]: Almacena en memoria la notificación en lugar de emitir llamada D-Bus org.freedesktop.Notifications.Notify.
        publishedLog.add("[Linux DBus] ${item.appName} - ${item.senderTitle}: ${item.messageBody}")
    }

    /**
     * Registra el acuse de resolución ejecutada desde el ordenador para actualizar el estado en el entorno de escritorio Linux.
     */
    override suspend fun dispatchResolutionFeedback(action: NotificationResolutionAction) {
        // [DUMMY-PRUEBAS]: Simula confirmación visual de acción en el centro de notificaciones GNOME/KDE.
        publishedLog.add("[Linux DBus Feedback] Acción ejecutada: $action")
    }
}
