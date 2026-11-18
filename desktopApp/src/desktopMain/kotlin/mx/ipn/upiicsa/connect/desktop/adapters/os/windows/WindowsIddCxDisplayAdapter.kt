package mx.ipn.upiicsa.connect.desktop.adapters.os.windows

import mx.ipn.upiicsa.connect.desktop.ports.outbound.OsVirtualDisplayOutboundPort
import mx.ipn.upiicsa.connect.shared.model.SecondaryDisplayConfig

/**
 * Adaptador exclusivo de Windows para gestionar pantallas virtuales secundarias mediante el controlador Indirect Display (IddCx).
 */
class WindowsIddCxDisplayAdapter : OsVirtualDisplayOutboundPort {

    private var activeConfig: SecondaryDisplayConfig? = null

    /**
     * Registra y habilita un monitor virtual secundario en el sistema gráfico de Windows.
     */
    override suspend fun startVirtualDisplay(config: SecondaryDisplayConfig): Boolean {
        // [DUMMY-PRUEBAS]: Simula el alta de pantalla virtual IddCx sin controlador KMDF/UMDF instalado.
        activeConfig = config
        return true
    }

    /**
     * Desconecta el monitor virtual secundario en Windows.
     */
    override suspend fun stopVirtualDisplay() {
        activeConfig = null
    }

    /**
     * Obtiene el cuadro capturado del escritorio extendido para enviarlo al dispositivo Android.
     */
    override suspend fun captureCurrentFrame(): ByteArray {
        val current = activeConfig ?: return ByteArray(0)
        // [DUMMY-PRUEBAS]: Genera encabezado sintético de frame DXGI simulando Desktop Duplication API.
        return "WIN_IDDCX_FRAME:${current.widthPx}x${current.heightPx}@${current.targetFps}".encodeToByteArray()
    }
}
