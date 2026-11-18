package mx.ipn.upiicsa.connect.desktop.adapters.os.linux

import mx.ipn.upiicsa.connect.desktop.ports.outbound.OsVirtualDisplayOutboundPort
import mx.ipn.upiicsa.connect.shared.model.SecondaryDisplayConfig

/**
 * Adaptador exclusivo de GNU/Linux para crear monitores virtuales secundarios mediante EVDI / PipeWire ScreenCast.
 */
class LinuxVirtualDisplayAdapter : OsVirtualDisplayOutboundPort {

    private var activeConfig: SecondaryDisplayConfig? = null

    /**
     * Inicializa una salida virtual secundaria en el compositor Wayland/X11 de GNU/Linux.
     */
    override suspend fun startVirtualDisplay(config: SecondaryDisplayConfig): Boolean {
        // [DUMMY-PRUEBAS]: Simula el alta de pantalla virtual DRM/EVDI en memoria sin requerir módulo de kernel cargado.
        activeConfig = config
        return true
    }

    /**
     * Desmonta la pantalla virtual secundaria en GNU/Linux.
     */
    override suspend fun stopVirtualDisplay() {
        activeConfig = null
    }

    /**
     * Captura un cuadro del monitor virtual para transmitirlo hacia la tablet o teléfono Android.
     */
    override suspend fun captureCurrentFrame(): ByteArray {
        val current = activeConfig ?: return ByteArray(0)
        // [DUMMY-PRUEBAS]: Genera encabezado sintético de cuadro en lugar de capturar buffer DMA-BUF de PipeWire.
        return "LINUX_EVDI_FRAME:${current.widthPx}x${current.heightPx}@${current.targetFps}".encodeToByteArray()
    }
}
