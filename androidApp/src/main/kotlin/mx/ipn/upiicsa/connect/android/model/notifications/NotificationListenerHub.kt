package mx.ipn.upiicsa.connect.android.model.notifications

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import mx.ipn.upiicsa.connect.shared.model.NotificationResolutionAction
import mx.ipn.upiicsa.connect.shared.model.PhoneNotificationItem

/**
 * Datos de una notificación del sistema ya extraídos de `StatusBarNotification`, sin depender de clases de Android.
 */
data class NotificationSnapshot(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val postTimeLabel: String,
    val isOngoing: Boolean,
    val isGroupSummary: Boolean,
    val hasReplyAction: Boolean
)

/**
 * Convierte instantáneas de notificaciones del sistema en elementos del modelo compartido y decide cuáles se espejan.
 */
object NotificationMapper {

    private const val MAX_BODY_CHARS = 2_000

    /**
     * Retorna el elemento espejable o `null` si la notificación es propia, persistente, un resumen de grupo o está vacía.
     */
    fun toItem(snapshot: NotificationSnapshot, ownPackage: String): PhoneNotificationItem? {
        if (snapshot.packageName == ownPackage || snapshot.isOngoing || snapshot.isGroupSummary) {
            return null
        }
        val title = snapshot.title?.trim().orEmpty()
        val body = (snapshot.bigText?.takeIf { it.isNotBlank() } ?: snapshot.text).orEmpty().trim()
        if (title.isEmpty() && body.isEmpty()) {
            return null
        }
        return PhoneNotificationItem(
            notificationId = snapshot.key,
            appName = snapshot.appLabel.ifBlank { snapshot.packageName },
            packageName = snapshot.packageName,
            senderTitle = title.ifEmpty { snapshot.appLabel.ifBlank { snapshot.packageName } },
            messageBody = body.take(MAX_BODY_CHARS),
            timestampLabel = snapshot.postTimeLabel,
            supportsInlineReply = snapshot.hasReplyAction
        )
    }
}

/**
 * Ejecuta en el sistema Android una resolución ordenada desde el ordenador; lo implementa el servicio de escucha.
 */
fun interface NotificationActionExecutor {

    /**
     * Aplica la acción sobre la notificación indicada; retorna `false` si ya no existe o no admite la acción.
     */
    fun execute(action: NotificationResolutionAction): Boolean
}

/**
 * Punto de encuentro entre el servicio de escucha de notificaciones (creado por Android) y el repositorio del modelo.
 */
class NotificationListenerHub {

    private val activeItems = MutableStateFlow<List<PhoneNotificationItem>>(emptyList())
    private val postedItems = MutableSharedFlow<PhoneNotificationItem>(extraBufferCapacity = 64)
    private val connected = MutableStateFlow(false)

    @Volatile
    private var executor: NotificationActionExecutor? = null

    /**
     * Notificaciones activas en el teléfono, la más reciente primero.
     */
    val active: StateFlow<List<PhoneNotificationItem>> = activeItems.asStateFlow()

    /**
     * Notificaciones nuevas o actualizadas desde que el servicio está conectado.
     */
    val posted: SharedFlow<PhoneNotificationItem> = postedItems.asSharedFlow()

    /**
     * Indica si Android tiene enlazado el servicio de escucha (acceso concedido y servicio activo).
     */
    val isListenerConnected: StateFlow<Boolean> = connected.asStateFlow()

    /**
     * Registra el servicio conectado con las notificaciones activas en ese momento.
     */
    fun onListenerConnected(executor: NotificationActionExecutor, initial: List<PhoneNotificationItem>) {
        this.executor = executor
        activeItems.value = initial
        connected.value = true
    }

    /**
     * Olvida el servicio desconectado por Android.
     */
    fun onListenerDisconnected() {
        executor = null
        connected.value = false
    }

    /**
     * Agrega o reemplaza una notificación publicada y la anuncia a los suscriptores.
     */
    fun onPosted(item: PhoneNotificationItem) {
        activeItems.update { current -> listOf(item) + current.filterNot { it.notificationId == item.notificationId } }
        postedItems.tryEmit(item)
    }

    /**
     * Retira una notificación eliminada del sistema.
     */
    fun onRemoved(notificationId: String) {
        activeItems.update { current -> current.filterNot { it.notificationId == notificationId } }
    }

    /**
     * Ejecuta la resolución en el sistema y marca la notificación como resuelta con un resumen legible.
     */
    fun resolve(action: NotificationResolutionAction): List<PhoneNotificationItem> {
        val (notificationId, summary) = when (action) {
            is NotificationResolutionAction.QuickReply -> action.notificationId to "Respondida desde PC: ${action.replyText}"
            is NotificationResolutionAction.MarkAsRead -> action.notificationId to "Marcada como leída desde PC"
            is NotificationResolutionAction.Dismiss -> action.notificationId to "Descartada desde PC"
        }
        val applied = executor?.execute(action) == true
        activeItems.update { current ->
            current.map { item ->
                if (item.notificationId != notificationId) {
                    item
                } else if (applied) {
                    item.copy(isResolved = true, resolutionSummary = summary)
                } else {
                    item.copy(resolutionSummary = "No se pudo aplicar en el teléfono")
                }
            }
        }
        return activeItems.value
    }

    companion object {

        /**
         * Instancia única del proceso: Android crea el servicio y el ViewModel por separado y ambos la comparten.
         */
        val shared: NotificationListenerHub = NotificationListenerHub()
    }
}
