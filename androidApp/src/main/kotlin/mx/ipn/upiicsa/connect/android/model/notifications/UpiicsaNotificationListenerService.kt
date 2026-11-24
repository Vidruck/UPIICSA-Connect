package mx.ipn.upiicsa.connect.android.model.notifications

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import mx.ipn.upiicsa.connect.shared.model.NotificationResolutionAction
import mx.ipn.upiicsa.connect.shared.model.PhoneNotificationItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Servicio que Android enlaza cuando el usuario concede el acceso a notificaciones: captura las notificaciones del
 * teléfono para espejarlas en el ordenador y ejecuta las respuestas, lecturas y descartes ordenados desde la PC.
 */
class UpiicsaNotificationListenerService : NotificationListenerService(), NotificationActionExecutor {

    private val hub: NotificationListenerHub = NotificationListenerHub.shared
    private val timeLabel: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

    /**
     * Registra el servicio en el punto de encuentro con las notificaciones activas en ese momento.
     */
    override fun onListenerConnected() {
        super.onListenerConnected()
        val initial = runCatching { activeNotifications.orEmpty().toList() }.getOrDefault(emptyList())
            .sortedByDescending { it.postTime }
            .mapNotNull { toItemOrNull(it) }
        hub.onListenerConnected(this, initial)
    }

    /**
     * Informa que Android desenlazó el servicio (acceso revocado o proceso detenido).
     */
    override fun onListenerDisconnected() {
        hub.onListenerDisconnected()
        super.onListenerDisconnected()
    }

    /**
     * Captura una notificación nueva o actualizada.
     */
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        toItemOrNull(sbn)?.let { hub.onPosted(it) }
    }

    /**
     * Retira del espejo una notificación eliminada en el teléfono.
     */
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        hub.onRemoved(sbn.key)
    }

    /**
     * Aplica en Android la resolución ordenada desde el ordenador.
     */
    override fun execute(action: NotificationResolutionAction): Boolean {
        val notificationId = when (action) {
            is NotificationResolutionAction.QuickReply -> action.notificationId
            is NotificationResolutionAction.MarkAsRead -> action.notificationId
            is NotificationResolutionAction.Dismiss -> action.notificationId
        }
        val sbn = runCatching { activeNotifications.orEmpty().firstOrNull { it.key == notificationId } }.getOrNull()
            ?: return false
        return when (action) {
            is NotificationResolutionAction.QuickReply -> sendInlineReply(sbn.notification, action.replyText)
            is NotificationResolutionAction.MarkAsRead -> markAsRead(sbn)
            is NotificationResolutionAction.Dismiss -> {
                cancelNotification(sbn.key)
                true
            }
        }
    }

    /**
     * Envía `text` mediante la acción con `RemoteInput` de texto libre, como lo haría la respuesta en línea del sistema.
     */
    private fun sendInlineReply(notification: Notification, text: String): Boolean {
        val replyAction = findReplyAction(notification) ?: return false
        val remoteInputs = replyAction.remoteInputs ?: return false
        val results = Bundle().apply { remoteInputs.forEach { putCharSequence(it.resultKey, text) } }
        val fillIn = Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        RemoteInput.addResultsToIntent(remoteInputs, fillIn, results)
        return sendQuietly(replyAction.actionIntent, fillIn)
    }

    /**
     * Usa la acción semántica «marcar como leída» (Android 9+) y, si la app no la ofrece, retira la notificación.
     */
    private fun markAsRead(sbn: StatusBarNotification): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val semantic = sbn.notification.actions.orEmpty()
                .firstOrNull { it.semanticAction == Notification.Action.SEMANTIC_ACTION_MARK_AS_READ }
            if (semantic != null && sendQuietly(semantic.actionIntent, null)) {
                return true
            }
        }
        cancelNotification(sbn.key)
        return true
    }

    /**
     * Busca la primera acción con `RemoteInput` de texto libre, incluidas las acciones para relojes (WearableExtender).
     */
    private fun findReplyAction(notification: Notification): Notification.Action? {
        val candidates = notification.actions.orEmpty().toList() + Notification.WearableExtender(notification).actions
        return candidates.firstOrNull { action -> action.remoteInputs.orEmpty().any { it.allowFreeFormInput } }
    }

    /**
     * Dispara la acción de la app dueña de la notificación; retorna `false` si la app la canceló o restringió el envío.
     */
    private fun sendQuietly(intent: PendingIntent?, fillIn: Intent?): Boolean {
        val target = intent ?: return false
        return try {
            target.send(this, 0, fillIn)
            true
        } catch (error: PendingIntent.CanceledException) {
            false
        } catch (error: SecurityException) {
            false
        }
    }

    /**
     * Extrae los datos de la notificación y aplica los filtros de [NotificationMapper].
     */
    private fun toItemOrNull(sbn: StatusBarNotification): PhoneNotificationItem? {
        val notification = sbn.notification ?: return null
        val extras = notification.extras ?: Bundle.EMPTY
        val snapshot = NotificationSnapshot(
            key = sbn.key,
            packageName = sbn.packageName,
            appLabel = applicationLabel(sbn.packageName, extras),
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            postTimeLabel = timeLabel.format(Instant.ofEpochMilli(sbn.postTime)),
            isOngoing = sbn.isOngoing,
            isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            hasReplyAction = findReplyAction(notification) != null
        )
        return NotificationMapper.toItem(snapshot, ownPackage = packageName)
    }

    /**
     * Nombre visible de la app: primero por el gestor de paquetes y, si la visibilidad de paquetes lo impide, con la
     * información de la app que Android adjunta a cada notificación.
     */
    private fun applicationLabel(targetPackage: String, extras: Bundle): String {
        val fromPackageManager = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(targetPackage, 0)).toString()
        } catch (error: PackageManager.NameNotFoundException) {
            null
        }
        if (!fromPackageManager.isNullOrBlank()) {
            return fromPackageManager
        }
        // [DECISIÓN TÉCNICA]: Android guarda la ApplicationInfo de la app emisora en el extra "android.appInfo"; se usa como respaldo cuando la visibilidad de paquetes (Android 11+) oculta la app.
        @Suppress("DEPRECATION")
        val appInfo = extras.getParcelable<ApplicationInfo>(EXTRA_APP_INFO)
        return appInfo?.loadLabel(packageManager)?.toString() ?: targetPackage
    }

    private companion object {
        const val EXTRA_APP_INFO = "android.appInfo"
    }
}
