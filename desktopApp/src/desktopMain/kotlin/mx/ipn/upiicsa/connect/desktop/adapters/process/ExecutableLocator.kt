package mx.ipn.upiicsa.connect.desktop.adapters.process

import java.io.File

/**
 * Localiza programas externos en las carpetas de la variable `PATH` sin ejecutarlos.
 */
object ExecutableLocator {

    /**
     * Indica si existe un archivo ejecutable llamado `name` en alguna carpeta de `pathVariable`.
     */
    fun isOnPath(name: String, pathVariable: String? = System.getenv("PATH")): Boolean =
        pathVariable.orEmpty()
            .split(File.pathSeparatorChar)
            .filter { it.isNotBlank() }
            .any { directory -> File(directory, name).let { it.isFile && it.canExecute() } }
}
