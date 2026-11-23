package mx.ipn.upiicsa.connect.desktop.architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Verifica automáticamente las reglas de dependencias de la arquitectura Ports & Adapters de CONTRIBUTING.md.
 */
class ArchitectureRulesTest {

    private val root = File(
        System.getProperty("upiicsa.sources.dir") ?: "src/desktopMain/kotlin"
    ).resolve("mx/ipn/upiicsa/connect/desktop")

    /**
     * Devuelve las líneas import de todos los archivos Kotlin bajo la carpeta relativa indicada.
     */
    private fun importsUnder(relative: String): Map<File, List<String>> =
        root.resolve(relative).walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .associateWith { file -> file.readLines().filter { it.startsWith("import ") } }

    /**
     * Falla listando los archivos que importan alguno de los paquetes prohibidos.
     */
    private fun assertNoImports(relative: String, vararg forbidden: String) {
        val files = importsUnder(relative)
        assertTrue(files.isNotEmpty(), "No se encontraron fuentes en $relative (${root.absolutePath})")
        val violations = files.flatMap { (file, imports) ->
            imports.filter { line -> forbidden.any { line.contains(it) } }.map { "${file.name}: $it" }
        }
        if (violations.isNotEmpty()) {
            fail("Violaciones de arquitectura en $relative:\n" + violations.joinToString("\n"))
        }
    }

    /**
     * Los adaptadores de GNU/Linux no deben depender de los de Windows.
     */
    @Test
    fun adaptadoresLinuxNoDependenDeWindows() = assertNoImports("adapters/os/linux", ".adapters.os.windows")

    /**
     * Los adaptadores de Windows no deben depender de los de GNU/Linux.
     */
    @Test
    fun adaptadoresWindowsNoDependenDeLinux() = assertNoImports("adapters/os/windows", ".adapters.os.linux")

    /**
     * La interfaz gráfica solo conoce puertos de entrada y dominio, nunca adaptadores.
     */
    @Test
    fun interfazNoDependeDeAdaptadores() = assertNoImports("ui", ".desktop.adapters.", ".desktop.application.", ".ports.outbound.")

    /**
     * El dominio es puro: no depende de UI, adaptadores, aplicación, puertos ni Compose.
     */
    @Test
    fun dominioEsPuro() = assertNoImports("domain", ".desktop.ui.", ".desktop.adapters.", ".desktop.application.", ".desktop.ports.", "androidx.")

    /**
     * La aplicación orquesta puertos sin conocer adaptadores concretos ni la interfaz.
     */
    @Test
    fun aplicacionNoDependeDeAdaptadoresNiUi() = assertNoImports("application", ".desktop.adapters.", ".desktop.ui.", "androidx.")

    /**
     * Ningún adaptador de sistema operativo supera el tamaño razonable de una clase de responsabilidad única.
     */
    @Test
    fun adaptadoresSinClasesDios() {
        val oversized = root.resolve("adapters/os").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readLines().size > 150 }
            .map { it.name }
            .toList()
        assertTrue(oversized.isEmpty(), "Adaptadores demasiado grandes (posible Clase Dios): $oversized")
    }
}
