package mx.ipn.upiicsa.connect.desktop.adapters.transport.tcp

import mx.ipn.upiicsa.connect.desktop.adapters.process.ProcessRunner

/**
 * Dispositivo Android visible para `adb` con depuración USB autorizada.
 */
data class AdbDevice(val serial: String, val model: String)

/**
 * Envoltura mínima de la herramienta `adb`: listado de dispositivos y túnel `adb reverse` hacia el servidor TCP local.
 */
class AdbDeviceBridge(
    private val runner: ProcessRunner,
    private val adbExecutable: String = System.getenv("UPIICSA_ADB") ?: "adb"
) {

    /**
     * Lista los dispositivos en estado `device` (los `unauthorized` u `offline` se omiten); vacío si `adb` no está instalado.
     */
    fun listDevices(): List<AdbDevice> {
        val result = runner.run(listOf(adbExecutable, "devices", "-l"), stdin = null, timeoutMs = COMMAND_TIMEOUT_MS)
        if (result == null || !result.succeeded) {
            return emptyList()
        }
        return parseDevicesOutput(result.stdout)
    }

    /**
     * Crea el túnel `adb reverse tcp:<puerto> tcp:<puerto>` para que el teléfono alcance el servidor del ordenador en `127.0.0.1`.
     */
    fun reverse(serial: String, port: Int): Boolean =
        runner.run(
            listOf(adbExecutable, "-s", serial, "reverse", "tcp:$port", "tcp:$port"),
            stdin = null,
            timeoutMs = COMMAND_TIMEOUT_MS
        )?.succeeded == true

    /**
     * Elimina el túnel creado por [reverse]; los errores se ignoran porque el dispositivo pudo desconectarse.
     */
    fun removeReverse(serial: String, port: Int) {
        runner.run(listOf(adbExecutable, "-s", serial, "reverse", "--remove", "tcp:$port"), stdin = null, timeoutMs = COMMAND_TIMEOUT_MS)
    }

    companion object {
        private const val COMMAND_TIMEOUT_MS = 8_000L

        /**
         * Interpreta la salida de `adb devices -l`, tomando el modelo del campo `model:` (guiones bajos como espacios).
         */
        fun parseDevicesOutput(output: String): List<AdbDevice> =
            output.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("*") && !it.startsWith("List of devices") }
                .mapNotNull { line ->
                    val fields = line.split(Regex("\\s+"))
                    if (fields.size < 2 || fields[1] != "device") {
                        return@mapNotNull null
                    }
                    val model = fields.firstOrNull { it.startsWith("model:") }
                        ?.removePrefix("model:")
                        ?.replace('_', ' ')
                        ?.takeIf { it.isNotBlank() }
                    AdbDevice(serial = fields[0], model = model ?: fields[0])
                }
                .toList()
    }
}
