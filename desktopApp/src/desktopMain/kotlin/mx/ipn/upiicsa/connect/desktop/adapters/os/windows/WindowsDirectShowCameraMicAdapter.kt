package mx.ipn.upiicsa.connect.desktop.adapters.os.windows

import mx.ipn.upiicsa.connect.desktop.ports.outbound.OsVirtualCameraMicOutboundPort

/**
 * Adaptador exclusivo de Windows para exponer la cámara y micrófono del móvil vía MediaFoundation/DirectShow y WASAPI virtual.
 */
class WindowsDirectShowCameraMicAdapter : OsVirtualCameraMicOutboundPort {

    private var cameraActive: Boolean = false
    private var microphoneActive: Boolean = false
    private var pushedFrames: Long = 0L

    /**
     * Habilita o deshabilita las fuentes virtuales de cámara y micrófono en Windows.
     */
    override suspend fun enableVirtualDevices(enableCamera: Boolean, enableMicrophone: Boolean): Boolean {
        // [DUMMY-PRUEBAS]: Simula registro de fuente virtual MediaFoundation/DirectShow en memoria.
        cameraActive = enableCamera
        microphoneActive = enableMicrophone
        return true
    }

    /**
     * Envía un cuadro de video de la cámara Android hacia el Frame Server virtual de Windows.
     */
    override suspend fun pushVideoFrame(frameBytes: ByteArray) {
        if (cameraActive && frameBytes.isNotEmpty()) {
            // [DUMMY-PRUEBAS]: Contabiliza cuadros recibidos sin invocar IMFMediaBuffer nativo.
            pushedFrames++
        }
    }

    /**
     * Entrega muestras de audio del micrófono del teléfono al controlador virtual de entrada de Windows.
     */
    override suspend fun pushMicAudio(pcmSamples: ShortArray) {
        if (microphoneActive && pcmSamples.isNotEmpty()) {
            // [DUMMY-PRUEBAS]: Acepta muestras PCM simulando inyección al micrófono virtual WASAPI.
            return
        }
    }
}
