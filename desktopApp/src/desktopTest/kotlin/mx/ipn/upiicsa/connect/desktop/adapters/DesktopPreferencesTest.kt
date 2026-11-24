package mx.ipn.upiicsa.connect.desktop.adapters

import mx.ipn.upiicsa.connect.desktop.adapters.preferences.FileDesktopPreferencesAdapter
import mx.ipn.upiicsa.connect.desktop.adapters.transport.DesktopLinkMode
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pruebas de la preferencia de modo de enlace guardada en Ajustes y de su prioridad frente a la variable de entorno.
 */
class DesktopPreferencesTest {

    /**
     * Verifica que la preferencia se guarde y se lea del archivo, y que un archivo dañado equivalga al simulador.
     */
    @Test
    fun guardaYLeePreferenciaDeEnlace() {
        val directory = Files.createTempDirectory("upiicsa-ajustes")
        val file = directory.resolve("sub/ajustes.properties")
        val preferences = FileDesktopPreferencesAdapter(file)
        assertFalse(preferences.isRealLinkPreferred())
        assertTrue(preferences.setRealLinkPreferred(true))
        assertTrue(FileDesktopPreferencesAdapter(file).isRealLinkPreferred())
        assertTrue(preferences.setRealLinkPreferred(false))
        assertFalse(FileDesktopPreferencesAdapter(file).isRealLinkPreferred())
        Files.writeString(file, "enlace=\\u00ZZ")
        assertFalse(FileDesktopPreferencesAdapter(file).isRealLinkPreferred())
        directory.toFile().deleteRecursively()
    }

    /**
     * Verifica la ruta del archivo en cada sistema operativo.
     */
    @Test
    fun rutaSegunSistemaOperativo() {
        assertEquals(
            Paths.get("C:\\Users\\ana\\AppData\\Roaming", "UPIICSA-Connect", "ajustes.properties"),
            FileDesktopPreferencesAdapter.defaultFile(mapOf("APPDATA" to "C:\\Users\\ana\\AppData\\Roaming"), "Windows 11", "C:\\Users\\ana")
        )
        assertEquals(
            Paths.get("/home/ana/.config", "upiicsa-connect", "ajustes.properties"),
            FileDesktopPreferencesAdapter.defaultFile(emptyMap(), "Linux", "/home/ana")
        )
        assertEquals(
            Paths.get("/xdg", "upiicsa-connect", "ajustes.properties"),
            FileDesktopPreferencesAdapter.defaultFile(mapOf("XDG_CONFIG_HOME" to "/xdg"), "Linux", "/home/ana")
        )
    }

    /**
     * Verifica que la propiedad o variable de entorno tengan prioridad sobre la preferencia guardada.
     */
    @Test
    fun entornoTienePrioridadSobreAjustes() {
        assertNull(DesktopLinkMode.environmentOverride(property = null, environment = null))
        assertEquals(DesktopLinkMode.REAL_TCP, DesktopLinkMode.environmentOverride(property = null, environment = "Real"))
        assertEquals(DesktopLinkMode.SIMULATED, DesktopLinkMode.environmentOverride(property = "simulado", environment = "real"))
        assertEquals(DesktopLinkMode.REAL_TCP, DesktopLinkMode.resolve(null, realLinkPreferred = true))
        assertEquals(DesktopLinkMode.SIMULATED, DesktopLinkMode.resolve(DesktopLinkMode.SIMULATED, realLinkPreferred = true))
        assertEquals(DesktopLinkMode.SIMULATED, DesktopLinkMode.resolve(null, realLinkPreferred = false))
    }
}
