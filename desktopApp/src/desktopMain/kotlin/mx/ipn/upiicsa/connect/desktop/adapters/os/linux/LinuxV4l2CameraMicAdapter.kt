package mx.ipn.upiicsa.connect.desktop.adapters.os.linux

import mx.ipn.upiicsa.connect.desktop.ports.outbound.OsVirtualCameraMicOutboundPort

/**
 * Adaptador exclusivo de GNU/Linux para inyectar cámara virtual (v4l2loopback) y micrófono virtual (PipeWire).
 */
class LinuxV4l2CameraMicAdapter : OsVirtualCameraMicOutboundPort {

    private var cameraActive: Boolean = false
    private var microphoneActive: Boolean = false
    private var injectedVideoFrames: Long = 0L

    /**
     * Habilita o deshabilita los nodos virtuales /dev/video y fuente de micrófono PipeWire en GNU/Linux.
     */
    override suspend fun enableVirtualDevices(enableCamera: Boolean, enableMicrophone: Boolean): Boolean {
        // [DUMMY-PRUEBAS]: Registra en memoria el estado virtual simulando apertura de v4l2loopback y pw-loopback.
        cameraActive = enableCamera
        microphoneActive = enableMicrophone
        return true
    }

    /**
     * Escribe un cuadro proveniente de la cámara del teléfono en el dispositivo virtual V4L2.
     */
    override suspend fun pushVideoFrame(frameBytes: ByteArray) {
        if (cameraActive && frameBytes.isNotEmpty()) {
            // [DUMMY-PRUEBAS]: Contabiliza tramas recibidas sin escribir en el descriptor de dispositivo /dev/video*.
            injectedVideoFrames++
        }
    }

    /**
     * Inyecta muestras de audio del micrófono móvil en el origen virtual de PipeWire para videollamadas.
     */
    override suspend fun pushMicAudio(pcmSamples: ShortArray) {
        if (microphoneActive && pcmSamples.isNotEmpty()) {
            // [DUMMY-PRUEBAS]: Acepta muestras PCM simulando entrega al nodo PipeWire de entrada.
            return
        }
    }
}
