package mx.ipn.upiicsa.connect.desktop.adapters.os.linux

/**
 * Utilidades de línea de comandos admitidas para el portapapeles de GNU/Linux, con sus comandos de lectura y escritura.
 */
enum class LinuxClipboardTool(
    val displayLabel: String,
    val readCommand: List<String>,
    val writeCommand: List<String>
) {
    WL_CLIPBOARD(
        displayLabel = "wl-clipboard (Wayland)",
        readCommand = listOf("wl-paste", "--no-newline", "--type", "text"),
        writeCommand = listOf("wl-copy")
    ),
    XCLIP(
        displayLabel = "xclip (X11)",
        readCommand = listOf("xclip", "-selection", "clipboard", "-o"),
        writeCommand = listOf("xclip", "-selection", "clipboard", "-i")
    ),
    XSEL(
        displayLabel = "xsel (X11)",
        readCommand = listOf("xsel", "--clipboard", "--output"),
        writeCommand = listOf("xsel", "--clipboard", "--input")
    );

    companion object {

        /**
         * Elige la utilidad según la sesión gráfica: Wayland con wl-clipboard y, si no, X11 (o XWayland) con xclip o xsel.
         * Retorna `null` si no hay sesión gráfica o ninguna utilidad está instalada.
         */
        fun detect(environment: Map<String, String>, isInstalled: (String) -> Boolean): LinuxClipboardTool? {
            val wayland = !environment["WAYLAND_DISPLAY"].isNullOrBlank()
            val x11 = !environment["DISPLAY"].isNullOrBlank()
            return when {
                wayland && isInstalled("wl-copy") && isInstalled("wl-paste") -> WL_CLIPBOARD
                x11 && isInstalled("xclip") -> XCLIP
                x11 && isInstalled("xsel") -> XSEL
                else -> null
            }
        }
    }
}
