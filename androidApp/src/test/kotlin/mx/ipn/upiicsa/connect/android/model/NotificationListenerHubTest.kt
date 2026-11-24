package mx.ipn.upiicsa.connect.android.model

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import mx.ipn.upiicsa.connect.android.model.notifications.NotificationListenerHub
import mx.ipn.upiicsa.connect.android.model.notifications.NotificationMapper
import mx.ipn.upiicsa.connect.android.model.notifications.NotificationSnapshot
import mx.ipn.upiicsa.connect.shared.model.NotificationResolutionAction
import mx.ipn.upiicsa.connect.shared.model.PhoneNotificationItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pruebas JVM (sin emulador) del filtrado de notificaciones del sistema y del punto de encuentro con el servicio.
 */
class NotificationListenerHubTest {

    private val base = NotificationSnapshot(
        key = "0|com.whatsapp|7|null|10123",
        packageName = "com.whatsapp",
        appLabel = "WhatsApp",
        title = "Equipo",
        text = "¿Listos?",
        bigText = null,
        postTimeLabel = "10:05",
        isOngoing = false,
        isGroupSummary = false,
        hasReplyAction = true
    )

    /**
     * Verifica la conversión y los filtros: app propia, persistentes, resúmenes de grupo y notificaciones vacías.
     */
    @Test
    fun filtraYConvierteNotificaciones() {
        val item = assertNotNull(NotificationMapper.toItem(base, ownPackage = "mx.ipn.upiicsa.connect.android"))
        assertEquals(base.key, item.notificationId)
        assertEquals("Equipo", item.senderTitle)
        assertEquals("¿Listos?", item.messageBody)
        assertTrue(item.supportsInlineReply)

        assertEquals("Texto largo", NotificationMapper.toItem(base.copy(bigText = "Texto largo"), "x")?.messageBody)
        assertEquals("WhatsApp", NotificationMapper.toItem(base.copy(title = null), "x")?.senderTitle)
        assertNull(NotificationMapper.toItem(base, ownPackage = "com.whatsapp"))
        assertNull(NotificationMapper.toItem(base.copy(isOngoing = true), "x"))
        assertNull(NotificationMapper.toItem(base.copy(isGroupSummary = true), "x"))
        assertNull(NotificationMapper.toItem(base.copy(title = " ", text = null), "x"))
    }

    /**
     * Verifica publicación, reemplazo, retiro y resolución con un ejecutor falso en lugar del servicio de Android.
     */
    @Test
    fun publicaRetiraYResuelve(): Unit = runBlocking {
        val hub = NotificationListenerHub()
        val executed = mutableListOf<NotificationResolutionAction>()
        hub.onListenerConnected({ action -> executed += action; action !is NotificationResolutionAction.MarkAsRead }, emptyList())
        assertTrue(hub.isListenerConnected.value)

        val received = launch { assertEquals("n-1", hub.posted.first().notificationId) }
        yield()
        hub.onPosted(item("n-1", "Hola"))
        received.join()
        hub.onPosted(item("n-2", "Adiós"))
        hub.onPosted(item("n-1", "Hola otra vez"))
        assertEquals(listOf("n-1", "n-2"), hub.active.value.map { it.notificationId })
        assertEquals("Hola otra vez", hub.active.value.first().messageBody)

        val afterReply = hub.resolve(NotificationResolutionAction.QuickReply("n-1", "Voy"))
        assertTrue(afterReply.first { it.notificationId == "n-1" }.isResolved)
        val afterFailure = hub.resolve(NotificationResolutionAction.MarkAsRead("n-2"))
        assertFalse(afterFailure.first { it.notificationId == "n-2" }.isResolved)
        assertEquals("No se pudo aplicar en el teléfono", afterFailure.first { it.notificationId == "n-2" }.resolutionSummary)
        assertEquals(2, executed.size)

        hub.onRemoved("n-2")
        assertEquals(listOf("n-1"), hub.active.value.map { it.notificationId })
        hub.onListenerDisconnected()
        assertFalse(hub.isListenerConnected.value)
        assertEquals("No se pudo aplicar en el teléfono", hub.resolve(NotificationResolutionAction.Dismiss("n-1")).first().resolutionSummary)
    }

    /**
     * Construye un elemento de prueba.
     */
    private fun item(id: String, body: String) =
        PhoneNotificationItem(id, "WhatsApp", "com.whatsapp", "Equipo", body, "10:00", supportsInlineReply = true)
}
