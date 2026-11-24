package mx.ipn.upiicsa.connect.desktop.adapters.os.linux

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mx.ipn.upiicsa.connect.desktop.adapters.os.AwtClipboardAccess
import mx.ipn.upiicsa.connect.desktop.adapters.process.ExecutableLocator
import mx.ipn.upiicsa.connect.desktop.adapters.process.ProcessRunner
import mx.ipn.upiicsa.connect.desktop.adapters.process.SystemProcessRunner
import mx.ipn.upiicsa.connect.desktop.ports.outbound.OsClipboardOutboundPort

/**
 * Adaptador exclusivo de GNU/Linux para el portapapeles de sesiones Wayland y X11.
 * Usa wl-clipboard, xclip o xsel cuando están instalados, AWT como respaldo y, sin sesión gráfica, una copia en memoria.
 */
class LinuxClipboardAdapter(
    private val runner: ProcessRunner? = null,
    private val tool: LinuxClipboardTool? = null,
    private val awtClipboard: AwtClipboardAccess? = null
) : OsClipboardOutboundPort {

    @Volatile
    private var cachedClipboard: String = ""

    /**
     * Mecanismo efectivo con el que se accede al portapapeles.
     */
    val backendLabel: String
        get() = when {
            runner != null && tool != null -> tool.displayLabel
            awtClipboard != null -> "AWT (sesión gráfica de la JVM)"
            else -> "Memoria (sin portapapeles del sistema)"
        }

    /**
     * Escribe texto en el portapapeles de GNU/Linux; la copia en memoria se conserva para lecturas fallidas.
     */
    override suspend fun writeClipboardText(text: String) {
        cachedClipboard = text
        withContext(Dispatchers.IO) {
            when {
                runner != null && tool != null -> runner.run(tool.writeCommand, text, TIMEOUT_MS)
                awtClipboard != null -> awtClipboard.write(text)
                else -> Unit
            }
        }
    }

    /**
     * Lee el texto vigente; un portapapeles vacío o sin texto retorna cadena vacía.
     */
    override suspend fun readClipboardText(): String = withContext(Dispatchers.IO) {
        when {
            runner != null && tool != null -> {
                val result = runner.run(tool.readCommand, null, TIMEOUT_MS)
                when {
                    result == null -> cachedClipboard
                    result.succeeded -> result.stdout
                    else -> ""
                }
            }
            awtClipboard != null -> awtClipboard.read() ?: cachedClipboard
            // [DECISIÓN TÉCNICA]: Sin sesión gráfica ni utilidades se conserva el texto en memoria para no interrumpir la sincronización con el teléfono.
            else -> cachedClipboard
        }
    }

    companion object {
        private const val TIMEOUT_MS = 2_000L

        /**
         * Construye el adaptador con la mejor opción disponible en el equipo anfitrión.
         */
        fun forHost(
            runner: ProcessRunner = SystemProcessRunner(),
            environment: Map<String, String> = System.getenv()
        ): LinuxClipboardAdapter {
            val tool = LinuxClipboardTool.detect(environment) { ExecutableLocator.isOnPath(it, environment["PATH"]) }
            return if (tool != null) {
                LinuxClipboardAdapter(runner, tool)
            } else {
                LinuxClipboardAdapter(awtClipboard = AwtClipboardAccess.createIfAvailable())
            }
        }
    }
}
