package mx.ipn.upiicsa.connect.desktop.adapters.os

import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

/**
 * Acceso al portapapeles del sistema mediante AWT, disponible solo cuando la JVM tiene sesión gráfica.
 * Es neutral respecto al sistema operativo: lo usa el adaptador de GNU/Linux como respaldo sin utilidades externas.
 */
class AwtClipboardAccess private constructor() {

    /**
     * Escribe texto plano; retorna `false` si el portapapeles está ocupado o no disponible.
     */
    fun write(text: String): Boolean = runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }.isSuccess

    /**
     * Lee el texto plano vigente; retorna `null` si no hay texto o el portapapeles no responde.
     */
    fun read(): String? = runCatching {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
            clipboard.getData(DataFlavor.stringFlavor) as? String
        } else {
            ""
        }
    }.getOrNull()

    companion object {

        /**
         * Retorna el acceso AWT si la JVM no es headless; en pruebas y servidores retorna `null`.
         */
        fun createIfAvailable(): AwtClipboardAccess? =
            if (GraphicsEnvironment.isHeadless()) null else AwtClipboardAccess()
    }
}
