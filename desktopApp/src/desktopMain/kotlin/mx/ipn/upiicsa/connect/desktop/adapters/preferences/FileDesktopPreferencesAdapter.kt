package mx.ipn.upiicsa.connect.desktop.adapters.preferences

import mx.ipn.upiicsa.connect.desktop.ports.outbound.DesktopPreferencesOutboundPort
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Properties

/**
 * Guarda las preferencias del escritorio en un archivo `.properties` de la carpeta de configuración del usuario
 * (`%APPDATA%\UPIICSA-Connect` en Windows; `$XDG_CONFIG_HOME/upiicsa-connect` o `~/.config/upiicsa-connect` en GNU/Linux).
 */
class FileDesktopPreferencesAdapter(private val file: Path = defaultFile()) : DesktopPreferencesOutboundPort {

    /**
     * Indica si el usuario eligió el enlace real; un archivo ausente o ilegible equivale al simulador.
     */
    override fun isRealLinkPreferred(): Boolean = load().getProperty(KEY_LINK_MODE) == VALUE_REAL

    /**
     * Guarda la preferencia de enlace; retorna `false` si no se pudo escribir el archivo.
     */
    override fun setRealLinkPreferred(enabled: Boolean): Boolean = try {
        val properties = load()
        properties.setProperty(KEY_LINK_MODE, if (enabled) VALUE_REAL else VALUE_SIMULATED)
        file.parent?.let { Files.createDirectories(it) }
        Files.newOutputStream(file).use { properties.store(it, "Preferencias de UPIICSA-Connect (escritorio)") }
        true
    } catch (error: IOException) {
        false
    }

    /**
     * Lee el archivo de preferencias; retorna propiedades vacías si no existe o está dañado.
     */
    private fun load(): Properties = Properties().apply {
        try {
            if (Files.isRegularFile(file)) {
                Files.newInputStream(file).use { load(it) }
            }
        } catch (error: IOException) {
            clear()
        } catch (error: IllegalArgumentException) {
            clear()
        }
    }

    companion object {
        private const val KEY_LINK_MODE = "enlace"
        private const val VALUE_REAL = "real"
        private const val VALUE_SIMULATED = "simulado"
        private const val FILE_NAME = "ajustes.properties"

        /**
         * Ruta del archivo de preferencias según el sistema operativo y las variables de entorno del usuario.
         */
        fun defaultFile(
            environment: Map<String, String> = System.getenv(),
            osName: String = System.getProperty("os.name").orEmpty(),
            userHome: String = System.getProperty("user.home").orEmpty()
        ): Path {
            val windows = osName.lowercase().contains("win")
            val directory = when {
                windows && !environment["APPDATA"].isNullOrBlank() -> Paths.get(environment.getValue("APPDATA"), "UPIICSA-Connect")
                windows -> Paths.get(userHome, "AppData", "Roaming", "UPIICSA-Connect")
                !environment["XDG_CONFIG_HOME"].isNullOrBlank() -> Paths.get(environment.getValue("XDG_CONFIG_HOME"), "upiicsa-connect")
                else -> Paths.get(userHome, ".config", "upiicsa-connect")
            }
            return directory.resolve(FILE_NAME)
        }
    }
}
