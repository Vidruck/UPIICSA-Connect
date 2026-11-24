package mx.ipn.upiicsa.connect.desktop.adapters.process

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Resultado de ejecutar un programa externo del sistema operativo.
 */
data class ProcessResult(val exitCode: Int, val stdout: String, val stderr: String) {

    /**
     * Indica si el programa terminó con código 0.
     */
    val succeeded: Boolean
        get() = exitCode == 0
}

/**
 * Ejecuta programas externos (adb, utilidades del portapapeles, etc.) sin intérprete de shell.
 * Las implementaciones de prueba devuelven salidas fijas para no depender del sistema anfitrión.
 */
fun interface ProcessRunner {

    /**
     * Ejecuta `command` con `stdin` opcional; retorna `null` si el programa no existe o excede `timeoutMs`.
     */
    fun run(command: List<String>, stdin: String?, timeoutMs: Long): ProcessResult?
}

/**
 * Implementación real basada en [ProcessBuilder]; los argumentos se pasan como lista, nunca concatenados en una cadena.
 */
class SystemProcessRunner : ProcessRunner {

    /**
     * Lanza el proceso, escribe `stdin` (si existe) y espera su fin con límite de tiempo.
     */
    override fun run(command: List<String>, stdin: String?, timeoutMs: Long): ProcessResult? {
        // [DECISIÓN TÉCNICA]: La salida se redirige a archivos temporales para que waitFor(timeout) acote de verdad la espera aunque el programa no cierre sus flujos.
        val stdoutFile = File.createTempFile("upiicsa-proc-", ".out")
        val stderrFile = File.createTempFile("upiicsa-proc-", ".err")
        try {
            val process = try {
                ProcessBuilder(command).redirectOutput(stdoutFile).redirectError(stderrFile).start()
            } catch (error: IOException) {
                return null
            }
            try {
                process.outputStream.use { output -> stdin?.let { output.write(it.encodeToByteArray()) } }
            } catch (error: IOException) {
                process.destroyForcibly()
                return null
            }
            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                return null
            }
            return ProcessResult(process.exitValue(), stdoutFile.readText(), stderrFile.readText())
        } finally {
            stdoutFile.delete()
            stderrFile.delete()
        }
    }
}
