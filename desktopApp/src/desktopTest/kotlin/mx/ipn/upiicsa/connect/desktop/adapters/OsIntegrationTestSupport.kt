package mx.ipn.upiicsa.connect.desktop.adapters

import mx.ipn.upiicsa.connect.desktop.adapters.process.ProcessResult
import mx.ipn.upiicsa.connect.desktop.adapters.process.ProcessRunner
import org.junit.Assume

/**
 * Utilidades compartidas por las pruebas de integración con el sistema operativo.
 */
object OsIntegrationTestSupport {

    /**
     * Variable de entorno que habilita las pruebas que tocan el portapapeles, las notificaciones o el audio reales.
     */
    const val REAL_OS_TESTS_VARIABLE = "UPIICSA_PRUEBAS_SO_REAL"

    /**
     * Omite la prueba salvo que la variable [REAL_OS_TESTS_VARIABLE] valga `1` (por ejemplo, en el trabajo de Windows del CI).
     */
    fun assumeRealOsTestsEnabled() {
        Assume.assumeTrue(
            "Prueba con el sistema real omitida; exporte $REAL_OS_TESTS_VARIABLE=1 para ejecutarla",
            System.getenv(REAL_OS_TESTS_VARIABLE) == "1"
        )
    }

    /**
     * Indica si las pruebas se ejecutan en Windows.
     */
    val isWindows: Boolean
        get() = System.getProperty("os.name").orEmpty().lowercase().contains("win")
}

/**
 * Ejecutor de procesos falso que registra cada comando con su entrada y responde con la función indicada.
 */
class RecordingProcessRunner(
    private val respond: (List<String>) -> ProcessResult? = { ProcessResult(0, "", "") }
) : ProcessRunner {
    val calls = mutableListOf<Pair<List<String>, String?>>()

    /**
     * Registra la invocación y retorna la respuesta configurada.
     */
    override fun run(command: List<String>, stdin: String?, timeoutMs: Long): ProcessResult? {
        synchronized(calls) { calls += command to stdin }
        return respond(command)
    }
}
