package mx.ipn.upiicsa.connect.desktop.adapters

import mx.ipn.upiicsa.connect.desktop.adapters.process.ProcessResult
import mx.ipn.upiicsa.connect.desktop.adapters.process.SystemProcessRunner
import mx.ipn.upiicsa.connect.desktop.adapters.transport.tcp.AdbDevice
import mx.ipn.upiicsa.connect.desktop.adapters.transport.tcp.AdbDeviceBridge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pruebas del envoltorio de `adb` con salidas capturadas y con el ejecutable real del anfitrión (si existe).
 */
class AdbDeviceBridgeTest {

    /**
     * Verifica que solo se acepten dispositivos autorizados y que el modelo se tome del campo `model:`.
     */
    @Test
    fun interpretaSalidaDeAdbDevices() {
        val salida = """
            * daemon not running; starting now at tcp:5037
            * daemon started successfully
            List of devices attached
            R58M12AB34C            device usb:1-1 product:o1sxxx model:SM_G991B device:o1s transport_id:2
            0A1B2C3D4E             unauthorized usb:1-2 transport_id:3
            emulator-5554          offline
            HT7A1B2C3              device usb:2-1 transport_id:4

        """.trimIndent()
        assertEquals(
            listOf(AdbDevice("R58M12AB34C", "SM G991B"), AdbDevice("HT7A1B2C3", "HT7A1B2C3")),
            AdbDeviceBridge.parseDevicesOutput(salida)
        )
    }

    /**
     * Verifica que el túnel use argumentos separados (sin shell) y que una falla de adb se reporte como `false`.
     */
    @Test
    fun construyeComandoReverseSinShell() {
        val comandos = mutableListOf<List<String>>()
        val bridge = AdbDeviceBridge(runner = { command, _, _ ->
            comandos += command
            ProcessResult(if (command.contains("--remove")) 1 else 0, "", "")
        })
        assertTrue(bridge.reverse("R58M; rm -rf /", 47800))
        bridge.removeReverse("R58M; rm -rf /", 47800)
        assertEquals(listOf("adb", "-s", "R58M; rm -rf /", "reverse", "tcp:47800", "tcp:47800"), comandos[0])
        assertEquals(listOf("adb", "-s", "R58M; rm -rf /", "reverse", "--remove", "tcp:47800"), comandos[1])
        assertFalse(AdbDeviceBridge(runner = { _, _, _ -> null }).reverse("x", 1))
    }

    /**
     * Ejecuta el `adb` real del anfitrión: sin teléfono (o sin adb instalado) debe devolver una lista sin fallar.
     */
    @Test
    fun adbRealSinTelefonoNoFalla() {
        val devices = AdbDeviceBridge(SystemProcessRunner()).listDevices()
        assertTrue(devices.all { it.serial.isNotBlank() })
    }
}
