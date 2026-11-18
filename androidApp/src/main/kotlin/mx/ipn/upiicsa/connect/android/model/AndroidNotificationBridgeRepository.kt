package mx.ipn.upiicsa.connect.android.model

import mx.ipn.upiicsa.connect.shared.model.NotificationResolutionAction
import mx.ipn.upiicsa.connect.shared.model.PhoneNotificationItem

/**
 * Repositorio de la capa Model (MVVM) para capturar notificaciones de Android y ejecutar su resolución remota.
 */
class AndroidNotificationBridgeRepository {

    private val cachedNotifications = mutableListOf(
        // [DUMMY-PRUEBAS]: Notificaciones iniciales de prueba en lugar de leer StatusBarNotification desde NotificationListenerService.
        PhoneNotificationItem(
            notificationId = "android-notif-1",
            appName = "WhatsApp",
            packageName = "com.whatsapp",
            senderTitle = "Laboratorio Redes",
            messageBody = "¿Ya probaste Music Mode por cable USB?",
            timestampLabel = "14:22",
            supportsInlineReply = true
        ),
        PhoneNotificationItem(
            notificationId = "android-notif-2",
            appName = "Correo IPN",
            packageName = "com.microsoft.office.outlook",
            senderTitle = "Academia Computación",
            messageBody = "Acta de evaluación disponible para revisión.",
            timestampLabel = "14:05",
            supportsInlineReply = false
        )
    )

    /**
     * Recupera las notificaciones activas capturadas en el dispositivo Android para espejarlas hacia la PC.
     */
    suspend fun loadActiveNotifications(): List<PhoneNotificationItem> {
        return cachedNotifications.toList()
    }

    /**
     * Aplica en Android una acción de resolución recibida desde el ordenador (RemoteInput, leída o descartar).
     */
    suspend fun resolveNotificationFromDesktop(action: NotificationResolutionAction): List<PhoneNotificationItem> {
        // [DUMMY-PRUEBAS]: Actualiza lista local simulando disparo de PendingIntent y RemoteInput sobre el sistema Android.
        val updated = cachedNotifications.map { item ->
            when (action) {
                is NotificationResolutionAction.QuickReply -> {
                    if (item.notificationId == action.notificationId) {
                        item.copy(isResolved = true, resolutionSummary = "Respondida desde PC: ${action.replyText}")
                    } else {
                        item
                    }
                }
                is NotificationResolutionAction.MarkAsRead -> {
                    if (item.notificationId == action.notificationId) {
                        item.copy(isResolved = true, resolutionSummary = "Marcada como leída desde PC")
                    } else {
                        item
                    }
                }
                is NotificationResolutionAction.Dismiss -> {
                    if (item.notificationId == action.notificationId) {
                        item.copy(isResolved = true, resolutionSummary = "Descartada desde PC")
                    } else {
                        item
                    }
                }
            }
        }
        cachedNotifications.clear()
        cachedNotifications.addAll(updated)
        return cachedNotifications.toList()
    }
}
