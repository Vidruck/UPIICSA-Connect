package mx.ipn.upiicsa.connect.desktop.adapters.os.windows

import mx.ipn.upiicsa.connect.desktop.ports.outbound.OsClipboardOutboundPort

/**
 * Adaptador exclusivo de Windows para lectura y escritura del portapapeles mediante la API Win32 User32.
 */
class WindowsWin32ClipboardAdapter : OsClipboardOutboundPort {

    private var cachedClipboard: String = ""

    /**
     * Escribe texto en el portapapeles de Windows.
     */
    override suspend fun writeClipboardText(text: String) {
        // [DUMMY-PRUEBAS]: Guarda el texto en memoria simulando OpenClipboard/SetClipboardData de user32.dll.
        cachedClipboard = text
    }

    /**
     * Lee el texto vigente del portapapeles de Windows.
     */
    override suspend fun readClipboardText(): String {
        // [DUMMY-PRUEBAS]: Retorna el texto en memoria simulando GetClipboardData(CF_UNICODETEXT).
        return cachedClipboard
    }
}
