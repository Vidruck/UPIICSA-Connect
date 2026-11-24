package mx.ipn.upiicsa.connect.android.model

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import mx.ipn.upiicsa.connect.shared.model.NotificationResolutionAction
import mx.ipn.upiicsa.connect.shared.model.PhoneNotificationItem

/**
 * Repositorio de la capa Model (MVVM) con notificaciones simuladas del teléfono, usado mientras el enlace real está apagado.
 */
class AndroidNotificationBridgeRepository : NotificationBridgeSource {

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
    override suspend fun loadActiveNotifications(): List<PhoneNotificationItem> {
        return cachedNotifications.toList()
    }

    /**
     * Aplica en Android una acción de resolución recibida desde el ordenador (RemoteInput, leída o descartar).
     */
    override suspend fun resolveNotificationFromDesktop(action: NotificationResolutionAction): List<PhoneNotificationItem> {
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

    /**
     * Flujo de notificaciones nuevas capturadas en el teléfono mientras el puente está activo.
     */
    override fun incomingNotifications(): Flow<PhoneNotificationItem> = flow {
        // [DUMMY-PRUEBAS]: Notificaciones periódicas simuladas en lugar de onNotificationPosted de NotificationListenerService.
        val catalog = listOf(
            Triple("Telegram", "org.telegram.messenger", "Club de Robótica" to "Reunión hoy en el edificio de Ingeniería."),
            Triple("Gmail", "com.google.android.gm", "Servicio Escolar UPIICSA" to "Tu constancia de estudios está lista."),
            Triple("WhatsApp", "com.whatsapp", "Mamá" to "¿Llegas a comer o te guardo?")
        )
        var index = 0
        while (true) {
            delay(25_000)
            val (app, pkg, content) = catalog[index % catalog.size]
            val item = PhoneNotificationItem(
                notificationId = "android-live-$index",
                appName = app,
                packageName = pkg,
                senderTitle = content.first,
                messageBody = content.second,
                timestampLabel = "Ahora",
                supportsInlineReply = app != "Gmail"
            )
            cachedNotifications.add(0, item)
            emit(item)
            index++
        }
    }
}
