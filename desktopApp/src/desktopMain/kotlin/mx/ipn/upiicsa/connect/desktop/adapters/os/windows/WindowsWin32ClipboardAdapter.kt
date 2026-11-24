package mx.ipn.upiicsa.connect.desktop.adapters.os.windows

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mx.ipn.upiicsa.connect.desktop.ports.outbound.OsClipboardOutboundPort

/**
 * Adaptador exclusivo de Windows para lectura y escritura del portapapeles mediante la API Win32 de user32.dll.
 * Sin la API (por ejemplo, en pruebas fuera de Windows) conserva el texto en memoria.
 */
class WindowsWin32ClipboardAdapter(
    private val api: Win32ClipboardApi? = null
) : OsClipboardOutboundPort {

    @Volatile
    private var cachedClipboard: String = ""

    /**
     * Mecanismo efectivo con el que se accede al portapapeles.
     */
    val backendLabel: String
        get() = if (api != null) "Win32 (user32.dll)" else "Memoria (sin portapapeles del sistema)"

    /**
     * Escribe texto en el portapapeles de Windows.
     */
    override suspend fun writeClipboardText(text: String) {
        cachedClipboard = text
        if (api != null) {
            withContext(Dispatchers.IO) { api.write(text) }
        }
    }

    /**
     * Lee el texto vigente del portapapeles de Windows; si está bloqueado por otro programa retorna el último texto conocido.
     */
    override suspend fun readClipboardText(): String {
        // [DECISIÓN TÉCNICA]: Sin user32.dll se conserva el texto en memoria para no interrumpir la sincronización con el teléfono.
        val clipboardApi = api ?: return cachedClipboard
        return withContext(Dispatchers.IO) { clipboardApi.read() ?: cachedClipboard }
    }

    companion object {

        /**
         * Construye el adaptador con la API Win32 si está disponible.
         */
        fun forHost(): WindowsWin32ClipboardAdapter = WindowsWin32ClipboardAdapter(Win32ClipboardApi.loadOrNull())
    }
}
