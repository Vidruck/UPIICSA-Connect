package mx.ipn.upiicsa.connect.desktop.adapters.os.linux

/**
 * Construye las invocaciones de `gdbus` hacia `org.freedesktop.Notifications`. Cada argumento viaja como literal de
 * texto GVariant escapado y en una posición propia de la lista (sin shell), por lo que el contenido de una notificación
 * del teléfono no puede alterar el comando.
 */
object FreedesktopNotifyCommands {

    private const val APP_NAME = "UPIICSA-Connect"
    private const val ICON_NAME = "phone"
    private const val MAX_TEXT_CHARS = 1_000
    private val baseCall = listOf(
        "gdbus", "call", "--session",
        "--dest", "org.freedesktop.Notifications",
        "--object-path", "/org/freedesktop/Notifications"
    )
    private val replyIdPattern = Regex("""uint32 (\d+)""")

    /**
     * Comando `Notify`; `replacesId` distinto de cero reemplaza una notificación ya mostrada.
     */
    fun notify(summary: String, body: String, replacesId: Long, expireTimeoutMs: Int): List<String> =
        baseCall + listOf(
            "--method", "org.freedesktop.Notifications.Notify",
            gvariantString(APP_NAME),
            "uint32 $replacesId",
            gvariantString(ICON_NAME),
            gvariantString(summary.take(MAX_TEXT_CHARS)),
            gvariantString(markupEscape(body.take(MAX_TEXT_CHARS))),
            "@as []",
            "@a{sv} {}",
            "int32 $expireTimeoutMs"
        )

    /**
     * Comando `CloseNotification` para retirar una notificación del centro de notificaciones.
     */
    fun close(notificationId: Long): List<String> =
        baseCall + listOf("--method", "org.freedesktop.Notifications.CloseNotification", "uint32 $notificationId")

    /**
     * Extrae el identificador de la respuesta de `Notify`, por ejemplo `(uint32 42,)`.
     */
    fun parseNotifyReply(stdout: String): Long? =
        replyIdPattern.find(stdout)?.groupValues?.get(1)?.toLongOrNull()

    /**
     * Escribe `value` como literal de cadena GVariant entre comillas simples, escapando comillas, barras y controles.
     */
    fun gvariantString(value: String): String = buildString {
        append('\'')
        for (char in value) {
            when {
                char == '\\' -> append("\\\\")
                char == '\'' -> append("\\'")
                char == '\n' -> append("\\n")
                char == '\t' -> append("\\t")
                char == '\r' -> append("\\r")
                char.code < 0x20 || char.code == 0x7F -> append("\\u%04x".format(char.code))
                else -> append(char)
            }
        }
        append('\'')
    }

    /**
     * Escapa el subconjunto de marcado que los servidores de notificaciones interpretan en el cuerpo.
     */
    fun markupEscape(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
