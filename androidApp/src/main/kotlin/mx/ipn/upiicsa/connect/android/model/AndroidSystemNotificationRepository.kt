package mx.ipn.upiicsa.connect.android.model

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.Flow
import mx.ipn.upiicsa.connect.android.model.notifications.NotificationListenerHub
import mx.ipn.upiicsa.connect.shared.model.NotificationResolutionAction
import mx.ipn.upiicsa.connect.shared.model.PhoneNotificationItem

/**
 * Fuente de notificaciones del teléfono consumida por el ViewModel: la simulada o la real del sistema.
 */
interface NotificationBridgeSource {

    /**
     * Notificaciones activas para espejar hacia la PC.
     */
    suspend fun loadActiveNotifications(): List<PhoneNotificationItem>

    /**
     * Aplica en Android una resolución ordenada desde el ordenador y retorna la lista actualizada.
     */
    suspend fun resolveNotificationFromDesktop(action: NotificationResolutionAction): List<PhoneNotificationItem>

    /**
     * Flujo de notificaciones nuevas mientras el puente está activo.
     */
    fun incomingNotifications(): Flow<PhoneNotificationItem>
}

/**
 * Repositorio (capa Model) de las notificaciones reales del teléfono capturadas por `UpiicsaNotificationListenerService`.
 * Requiere que el usuario conceda el «Acceso a notificaciones» en los ajustes de Android.
 */
class AndroidSystemNotificationRepository(
    private val context: Context,
    private val hub: NotificationListenerHub = NotificationListenerHub.shared
) : NotificationBridgeSource {

    /**
     * Indica si el usuario concedió el acceso a notificaciones a esta app.
     */
    fun isAccessGranted(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /**
     * Notificaciones activas capturadas por el servicio (vacía si aún no se concede el acceso).
     */
    override suspend fun loadActiveNotifications(): List<PhoneNotificationItem> = hub.active.value

    /**
     * Responde, marca como leída o descarta la notificación real en el teléfono.
     */
    override suspend fun resolveNotificationFromDesktop(action: NotificationResolutionAction): List<PhoneNotificationItem> =
        hub.resolve(action)

    /**
     * Notificaciones publicadas en el teléfono desde que el servicio está conectado.
     */
    override fun incomingNotifications(): Flow<PhoneNotificationItem> = hub.posted
}
