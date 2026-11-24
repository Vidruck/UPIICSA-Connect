package mx.ipn.upiicsa.connect.desktop.adapters

import kotlinx.coroutines.runBlocking
import mx.ipn.upiicsa.connect.desktop.adapters.os.linux.LinuxClipboardAdapter
import mx.ipn.upiicsa.connect.desktop.adapters.os.linux.LinuxClipboardTool
import mx.ipn.upiicsa.connect.desktop.adapters.os.windows.Win32ClipboardApi
import mx.ipn.upiicsa.connect.desktop.adapters.os.windows.WindowsWin32ClipboardAdapter
import mx.ipn.upiicsa.connect.desktop.adapters.process.ProcessResult
import mx.ipn.upiicsa.connect.desktop.adapters.process.SystemProcessRunner
import org.junit.Assume
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Pruebas de los adaptadores de portapapeles de GNU/Linux y Windows con procesos falsos y, si se habilita, con el sistema real.
 */
class ClipboardAdaptersTest {

    /**
     * Verifica la elección de utilidad según la sesión gráfica y los programas instalados.
     */
    @Test
    fun detectaUtilidadSegunSesion() {
        val todos: (String) -> Boolean = { true }
        val soloXclip: (String) -> Boolean = { it == "xclip" }
        assertEquals(LinuxClipboardTool.WL_CLIPBOARD, LinuxClipboardTool.detect(mapOf("WAYLAND_DISPLAY" to "wayland-0", "DISPLAY" to ":0"), todos))
        assertEquals(LinuxClipboardTool.XCLIP, LinuxClipboardTool.detect(mapOf("WAYLAND_DISPLAY" to "wayland-0", "DISPLAY" to ":0"), soloXclip))
        assertEquals(LinuxClipboardTool.XSEL, LinuxClipboardTool.detect(mapOf("DISPLAY" to ":0")) { it == "xsel" })
        assertNull(LinuxClipboardTool.detect(mapOf("WAYLAND_DISPLAY" to "wayland-0"), soloXclip))
        assertNull(LinuxClipboardTool.detect(emptyMap(), todos))
    }

    /**
     * Verifica que el texto viaje por la entrada estándar (nunca como argumento) y la interpretación de las lecturas.
     */
    @Test
    fun escribeYLeeConLaUtilidadExterna() = runBlocking {
        var lectura: ProcessResult? = ProcessResult(0, "texto 'raro' \$(rm -rf ~)\nsegunda línea", "")
        val runner = RecordingProcessRunner { command -> if (command.first() == "wl-paste") lectura else ProcessResult(0, "", "") }
        val adapter = LinuxClipboardAdapter(runner, LinuxClipboardTool.WL_CLIPBOARD)

        adapter.writeClipboardText("hola; rm -rf ~")
        assertEquals(listOf("wl-copy") to "hola; rm -rf ~", runner.calls.single())
        assertEquals("texto 'raro' \$(rm -rf ~)\nsegunda línea", adapter.readClipboardText())

        lectura = ProcessResult(1, "", "Nothing is copied")
        assertEquals("", adapter.readClipboardText())
        lectura = null
        assertEquals("hola; rm -rf ~", adapter.readClipboardText())
    }

    /**
     * Verifica que sin user32.dll (fuera de Windows) el adaptador conserve el texto en memoria.
     */
    @Test
    fun windowsSinUser32UsaMemoria() = runBlocking {
        Assume.assumeFalse(OsIntegrationTestSupport.isWindows)
        assertNull(Win32ClipboardApi.loadOrNull())
        val adapter = WindowsWin32ClipboardAdapter.forHost()
        adapter.writeClipboardText("en memoria")
        assertEquals("en memoria", adapter.readClipboardText())
    }

    /**
     * Ida y vuelta con el portapapeles real de Windows (Unicode fuera del plano básico incluido).
     */
    @Test
    fun windowsPortapapelesRealIdaYVuelta() = runBlocking {
        Assume.assumeTrue(OsIntegrationTestSupport.isWindows)
        OsIntegrationTestSupport.assumeRealOsTestsEnabled()
        val api = assertNotNull(Win32ClipboardApi.loadOrNull())
        val adapter = WindowsWin32ClipboardAdapter(api)
        val texto = "UPIICSA-Connect ñ ü 漢字 🚀\r\nlínea 2"
        adapter.writeClipboardText(texto)
        assertEquals(texto, adapter.readClipboardText())
    }

    /**
     * Ida y vuelta con el portapapeles real de GNU/Linux si hay sesión gráfica y wl-clipboard, xclip o xsel.
     */
    @Test
    fun linuxPortapapelesRealIdaYVuelta() = runBlocking {
        Assume.assumeFalse(OsIntegrationTestSupport.isWindows)
        OsIntegrationTestSupport.assumeRealOsTestsEnabled()
        val adapter = LinuxClipboardAdapter.forHost(SystemProcessRunner())
        Assume.assumeFalse("Sin utilidad de portapapeles", adapter.backendLabel.startsWith("Memoria"))
        val texto = "UPIICSA-Connect ñ 🚀 línea 1\nlínea 2"
        adapter.writeClipboardText(texto)
        assertEquals(texto, adapter.readClipboardText())
    }
}
