package mx.ipn.upiicsa.connect.desktop.adapters

import kotlinx.coroutines.runBlocking
import mx.ipn.upiicsa.connect.desktop.adapters.os.linux.FreedesktopNotifyCommands
import mx.ipn.upiicsa.connect.desktop.adapters.os.linux.LinuxFreedesktopNotificationAdapter
import mx.ipn.upiicsa.connect.desktop.adapters.os.windows.WindowsToastScripts
import mx.ipn.upiicsa.connect.desktop.adapters.process.ExecutableLocator
import mx.ipn.upiicsa.connect.desktop.adapters.process.ProcessResult
import mx.ipn.upiicsa.connect.desktop.adapters.process.SystemProcessRunner
import mx.ipn.upiicsa.connect.shared.model.NotificationResolutionAction
import mx.ipn.upiicsa.connect.shared.model.PhoneNotificationItem
import org.junit.Assume
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Pruebas de los adaptadores de notificaciones: constructores de comandos (sin sistema) y, si se habilita, el sistema real.
 */
class NotificationAdaptersTest {

    private val hostil = "Ana'); Remove-Item C:\\ -Recurse; ('<b>&\"\u2019\n\u0007"

    /**
     * Verifica el escape de literales GVariant y del marcado del cuerpo.
     */
    @Test
    fun escapaLiteralesGVariantYMarcado() {
        assertEquals("""'Ana\'s \\ \n\u0007'""", FreedesktopNotifyCommands.gvariantString("Ana's \\ \n\u0007"))
        assertEquals("a &lt;b&gt; &amp; c", FreedesktopNotifyCommands.markupEscape("a <b> & c"))
        assertEquals(42L, FreedesktopNotifyCommands.parseNotifyReply("(uint32 42,)\n"))
    }

    /**
     * Verifica que cada dato del teléfono ocupe una sola posición de la lista de argumentos de `gdbus`.
     */
    @Test
    fun comandoNotifyAisladoPorArgumento() {
        val command = FreedesktopNotifyCommands.notify(hostil, hostil, replacesId = 7, expireTimeoutMs = 8_000)
        assertEquals("gdbus", command.first())
        assertEquals(17, command.size)
        assertEquals("uint32 7", command[10])
        assertEquals(FreedesktopNotifyCommands.gvariantString(hostil), command[12])
        assertTrue(command.none { it == "Ana');" })
    }

    /**
     * Verifica que el adaptador recuerde el identificador del escritorio para reemplazar y cerrar la notificación.
     */
    @Test
    fun linuxReemplazaYCierraNotificacion() = runBlocking {
        val runner = RecordingProcessRunner { ProcessResult(0, "(uint32 31,)\n", "") }
        val adapter = LinuxFreedesktopNotificationAdapter(runner)
        adapter.publishPhoneNotification(NOTIFICACION)
        adapter.publishPhoneNotification(NOTIFICACION)
        adapter.dispatchResolutionFeedback(NotificationResolutionAction.Dismiss("n-1"))
        adapter.dispatchResolutionFeedback(NotificationResolutionAction.Dismiss("n-1"))

        assertEquals(3, runner.calls.size)
        assertEquals("uint32 0", runner.calls[0].first[10])
        assertEquals("uint32 31", runner.calls[1].first[10])
        assertEquals(FreedesktopNotifyCommands.close(31), runner.calls[2].first)
    }

    /**
     * Verifica que el contenido del toast solo aparezca codificado en Base64 dentro del script de PowerShell.
     */
    @Test
    fun scriptToastNoInterpolaContenido() {
        val script = WindowsToastScripts.showScript("n-1", hostil, hostil)
        assertFalse(script.contains("Remove-Item"))
        val encoded = Regex("""FromBase64String\('([A-Za-z0-9+/=]+)'\)""").find(script)?.groupValues?.get(1)
        val xml = String(Base64.getDecoder().decode(assertNotNull(encoded)), Charsets.UTF_8)
        assertTrue(xml.contains("Ana&apos;); Remove-Item C:\\ -Recurse; (&apos;&lt;b&gt;&amp;&quot;\u2019\n"))
        assertFalse(xml.contains('\u0007'))
        assertTrue(Regex("^[0-9a-f]{16}$").matches(WindowsToastScripts.tagFor("n-1")))

        val command = WindowsToastScripts.command(script)
        assertEquals("-EncodedCommand", command[command.size - 2])
        assertEquals(script, String(Base64.getDecoder().decode(command.last()), Charsets.UTF_16LE))
    }

    /**
     * Verifica en Windows que PowerShell analice sin errores los scripts generados (no muestra nada).
     */
    @Test
    fun windowsPowerShellAnalizaLosScripts() {
        Assume.assumeTrue(OsIntegrationTestSupport.isWindows)
        for (script in listOf(WindowsToastScripts.showScript("n-1", hostil, hostil), WindowsToastScripts.removeScript("n-1"))) {
            val encoded = Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))
            val checker = "\$errors = \$null; " +
                "[System.Management.Automation.Language.Parser]::ParseInput(" +
                "[System.Text.Encoding]::Unicode.GetString([System.Convert]::FromBase64String('$encoded')), [ref]\$null, [ref]\$errors) | Out-Null; " +
                "if (\$errors.Count -gt 0) { \$errors | ForEach-Object { \$_.Message }; exit 1 }"
            val result = assertNotNull(SystemProcessRunner().run(WindowsToastScripts.command(checker), null, 60_000))
            assertEquals(0, result.exitCode, result.stdout + result.stderr)
        }
    }

    /**
     * Muestra y retira un toast real en Windows (solo con la variable de pruebas del sistema real).
     */
    @Test
    fun windowsToastReal() {
        Assume.assumeTrue(OsIntegrationTestSupport.isWindows)
        OsIntegrationTestSupport.assumeRealOsTestsEnabled()
        val runner = SystemProcessRunner()
        val show = assertNotNull(runner.run(WindowsToastScripts.command(WindowsToastScripts.showScript("prueba", "UPIICSA-Connect", hostil)), null, 60_000))
        assertEquals(0, show.exitCode, show.stdout + show.stderr)
        val remove = assertNotNull(runner.run(WindowsToastScripts.command(WindowsToastScripts.removeScript("prueba")), null, 60_000))
        assertEquals(0, remove.exitCode, remove.stdout + remove.stderr)
    }

    /**
     * Publica y cierra una notificación real por D-Bus en GNU/Linux (solo con la variable de pruebas del sistema real).
     */
    @Test
    fun linuxNotificacionRealPorDbus() {
        Assume.assumeFalse(OsIntegrationTestSupport.isWindows)
        OsIntegrationTestSupport.assumeRealOsTestsEnabled()
        Assume.assumeTrue(ExecutableLocator.isOnPath("gdbus"))
        val runner = SystemProcessRunner()
        val notify = assertNotNull(runner.run(FreedesktopNotifyCommands.notify("UPIICSA-Connect: prueba", hostil, 0, 3_000), null, 5_000))
        assertEquals(0, notify.exitCode, notify.stderr)
        val desktopId = assertNotNull(FreedesktopNotifyCommands.parseNotifyReply(notify.stdout))
        val close = assertNotNull(runner.run(FreedesktopNotifyCommands.close(desktopId), null, 5_000))
        assertEquals(0, close.exitCode, close.stderr)
    }

    private companion object {
        val NOTIFICACION = PhoneNotificationItem("n-1", "WhatsApp", "com.whatsapp", "Equipo", "¿Listos?", "10:00", supportsInlineReply = true)
    }
}
