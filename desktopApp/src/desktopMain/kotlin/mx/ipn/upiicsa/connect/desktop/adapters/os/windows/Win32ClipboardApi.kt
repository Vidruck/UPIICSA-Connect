package mx.ipn.upiicsa.connect.desktop.adapters.os.windows

import com.sun.jna.Native

/**
 * Acceso a texto Unicode (`CF_UNICODETEXT`) del portapapeles Win32 mediante OpenClipboard/GetClipboardData/SetClipboardData.
 */
class Win32ClipboardApi internal constructor(
    private val user32: User32ClipboardLibrary,
    private val kernel32: Kernel32GlobalMemoryLibrary
) {

    /**
     * Copia `text` al portapapeles; retorna `false` si otro programa lo mantiene abierto.
     */
    fun write(text: String): Boolean = withOpenClipboard {
        user32.EmptyClipboard()
        val bytes = (text + '\u0000').toByteArray(Charsets.UTF_16LE)
        val handle = kernel32.GlobalAlloc(GMEM_MOVEABLE, bytes.size.toLong()) ?: return@withOpenClipboard false
        val target = kernel32.GlobalLock(handle)
        if (target == null) {
            kernel32.GlobalFree(handle)
            return@withOpenClipboard false
        }
        target.write(0, bytes, 0, bytes.size)
        kernel32.GlobalUnlock(handle)
        // [DECISIÓN TÉCNICA]: Si SetClipboardData acepta el bloque, Windows pasa a ser su dueño y no debe liberarse aquí.
        if (user32.SetClipboardData(CF_UNICODETEXT, handle) == null) {
            kernel32.GlobalFree(handle)
            false
        } else {
            true
        }
    } ?: false

    /**
     * Lee el texto Unicode vigente; retorna cadena vacía si no hay texto y `null` si el portapapeles no se pudo abrir.
     */
    fun read(): String? = withOpenClipboard {
        if (!user32.IsClipboardFormatAvailable(CF_UNICODETEXT)) {
            return@withOpenClipboard ""
        }
        val handle = user32.GetClipboardData(CF_UNICODETEXT) ?: return@withOpenClipboard ""
        val source = kernel32.GlobalLock(handle) ?: return@withOpenClipboard ""
        try {
            source.getWideString(0)
        } finally {
            kernel32.GlobalUnlock(handle)
        }
    }

    /**
     * Abre el portapapeles con reintentos breves, ejecuta `block` y lo cierra siempre.
     */
    private fun <T> withOpenClipboard(block: () -> T): T? {
        repeat(OPEN_ATTEMPTS) { attempt ->
            if (user32.OpenClipboard(null)) {
                try {
                    return block()
                } finally {
                    user32.CloseClipboard()
                }
            }
            if (attempt < OPEN_ATTEMPTS - 1) {
                Thread.sleep(OPEN_RETRY_DELAY_MS)
            }
        }
        return null
    }

    companion object {
        private const val CF_UNICODETEXT = 13
        private const val GMEM_MOVEABLE = 0x0002
        private const val OPEN_ATTEMPTS = 5
        private const val OPEN_RETRY_DELAY_MS = 20L

        /**
         * Carga user32.dll y kernel32.dll; retorna `null` fuera de Windows o si JNA no puede vincularlas.
         */
        fun loadOrNull(): Win32ClipboardApi? = try {
            Win32ClipboardApi(
                Native.load("user32", User32ClipboardLibrary::class.java),
                Native.load("kernel32", Kernel32GlobalMemoryLibrary::class.java)
            )
        } catch (error: LinkageError) {
            null
        }
    }
}
