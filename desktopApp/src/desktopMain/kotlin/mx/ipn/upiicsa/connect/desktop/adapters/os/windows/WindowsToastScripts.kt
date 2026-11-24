package mx.ipn.upiicsa.connect.desktop.adapters.os.windows

import java.security.MessageDigest
import java.util.Base64

/**
 * Construye los scripts de PowerShell que muestran y retiran notificaciones *toast* de Windows (WinRT).
 * El contenido de la notificación nunca se interpola como código: viaja en Base64 y se decodifica dentro del script,
 * y el script completo se entrega con `-EncodedCommand`, sin shell ni comillas.
 */
object WindowsToastScripts {

    /**
     * AppUserModelID registrado por Windows PowerShell, que permite mostrar toasts sin instalar un acceso directo propio.
     */
    const val POWERSHELL_APP_ID = "{1AC14E77-02E7-4E5D-B744-2EB1AE5198B7}\\WindowsPowerShell\\v1.0\\powershell.exe"
    private const val GROUP = "upiicsa-connect"
    private const val MAX_TEXT_CHARS = 1_000
    private const val WINRT_TYPES =
        "[Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime] | Out-Null\n" +
            "[Windows.Data.Xml.Dom.XmlDocument, Windows.Data.Xml.Dom.XmlDocument, ContentType = WindowsRuntime] | Out-Null\n"

    /**
     * Documento XML del toast con título y cuerpo escapados.
     */
    fun toastXml(title: String, body: String): String =
        "<toast><visual><binding template=\"ToastGeneric\">" +
            "<text>${xmlEscape(title.take(MAX_TEXT_CHARS))}</text>" +
            "<text>${xmlEscape(body.take(MAX_TEXT_CHARS))}</text>" +
            "<text placement=\"attribution\">UPIICSA-Connect</text>" +
            "</binding></visual></toast>"

    /**
     * Script que muestra el toast con una etiqueta derivada del identificador de la notificación del teléfono.
     */
    fun showScript(notificationId: String, title: String, body: String): String {
        // [DECISIÓN TÉCNICA]: Con '$ErrorActionPreference = Stop' cualquier falla de WinRT termina el proceso con código distinto de cero.
        val encodedXml = Base64.getEncoder().encodeToString(toastXml(title, body).toByteArray(Charsets.UTF_8))
        return "\$ErrorActionPreference = 'Stop'\n" + WINRT_TYPES +
            "\$xml = New-Object Windows.Data.Xml.Dom.XmlDocument\n" +
            "\$xml.LoadXml([System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String('$encodedXml')))\n" +
            "\$toast = New-Object Windows.UI.Notifications.ToastNotification \$xml\n" +
            "\$toast.Tag = '${tagFor(notificationId)}'\n" +
            "\$toast.Group = '$GROUP'\n" +
            "[Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier('$POWERSHELL_APP_ID').Show(\$toast)\n"
    }

    /**
     * Script que retira del Centro de actividades el toast asociado a la notificación del teléfono.
     */
    fun removeScript(notificationId: String): String =
        "\$ErrorActionPreference = 'Stop'\n" + WINRT_TYPES +
            "[Windows.UI.Notifications.ToastNotificationManager]::History.Remove(" +
            "'${tagFor(notificationId)}', '$GROUP', '$POWERSHELL_APP_ID')\n"

    /**
     * Línea de comandos de PowerShell que ejecuta `script` codificado en UTF-16LE y Base64.
     */
    fun command(script: String): List<String> = listOf(
        "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
        "-EncodedCommand", Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))
    )

    /**
     * Etiqueta hexadecimal de 16 caracteres (Windows limita la longitud de `Tag`) derivada con SHA-256.
     */
    fun tagFor(notificationId: String): String =
        MessageDigest.getInstance("SHA-256").digest(notificationId.toByteArray(Charsets.UTF_8))
            .take(8)
            .joinToString("") { "%02x".format(it) }

    /**
     * Escapa texto para XML 1.0 y descarta caracteres de control no permitidos.
     */
    fun xmlEscape(value: String): String = buildString {
        for (char in value) {
            when {
                char == '&' -> append("&amp;")
                char == '<' -> append("&lt;")
                char == '>' -> append("&gt;")
                char == '"' -> append("&quot;")
                char == '\'' -> append("&apos;")
                char == '\n' || char == '\t' || char == '\r' -> append(char)
                char.code < 0x20 || char == '￾' || char == '￿' -> Unit
                else -> append(char)
            }
        }
    }
}
