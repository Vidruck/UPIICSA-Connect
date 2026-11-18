package mx.ipn.upiicsa.connect.desktop.adapters.os.linux

import mx.ipn.upiicsa.connect.desktop.ports.outbound.OsClipboardOutboundPort

/**
 * Adaptador exclusivo de GNU/Linux para sincronizar el portapapeles de sesiones Wayland y X11.
 */
class LinuxClipboardAdapter : OsClipboardOutboundPort {

    private var cachedClipboard: String = ""

    /**
     * Escribe texto en el portapapeles de GNU/Linux.
     */
    override suspend fun writeClipboardText(text: String) {
        // [DUMMY-PRUEBAS]: Almacena en memoria local simulando wl-copy / xclip hasta integrar JNI/X11-Wayland.
        cachedClipboard = text
    }

    /**
     * Lee el texto vigente del portapapeles en GNU/Linux.
     */
    override suspend fun readClipboardText(): String {
        // [DUMMY-PRUEBAS]: Retorna el estado en memoria simulando wl-paste / xclip.
        return cachedClipboard
    }
}
