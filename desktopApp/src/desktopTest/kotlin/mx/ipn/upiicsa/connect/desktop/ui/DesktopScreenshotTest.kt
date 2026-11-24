package mx.ipn.upiicsa.connect.desktop.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import mx.ipn.upiicsa.connect.desktop.adapters.os.OsAdapterFactory
import mx.ipn.upiicsa.connect.desktop.adapters.os.linux.LinuxPipeWireAudioSinkAdapter
import mx.ipn.upiicsa.connect.desktop.adapters.simulation.DummyPhoneEventStreamAdapter
import mx.ipn.upiicsa.connect.desktop.adapters.transport.MultiChannelTransportAdapter
import mx.ipn.upiicsa.connect.desktop.application.DesktopConnectionService
import mx.ipn.upiicsa.connect.desktop.domain.DesktopSessionAggregate
import mx.ipn.upiicsa.connect.shared.model.ConnectionDirection
import mx.ipn.upiicsa.connect.shared.model.DesktopOsPlatform
import mx.ipn.upiicsa.connect.shared.model.NotificationResolutionAction
import mx.ipn.upiicsa.connect.shared.model.SecondaryDisplayConfig
import mx.ipn.upiicsa.connect.shared.model.ThemePreference
import mx.ipn.upiicsa.connect.shared.protocol.DummyRustCoreBridge
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renderiza fuera de pantalla cada sección del panel en modo claro y oscuro y guarda capturas PNG para revisión visual.
 */
class DesktopScreenshotTest {

    private val outputDir = File(System.getProperty("upiicsa.screenshots.dir") ?: "build/screenshots")

    /**
     * Construye un servicio con dispositivo enlazado y servicios activos usando los adaptadores dummy.
     */
    private fun servicioConSesionActiva(): DesktopConnectionService = runBlocking {
        val rustCore = DummyRustCoreBridge()
        val bundle = OsAdapterFactory.createPortBundle(DesktopOsPlatform.GNU_LINUX)
        val phone = DummyPhoneEventStreamAdapter(tickMillis = 0L)
        val service = DesktopConnectionService(
            aggregate = DesktopSessionAggregate(),
            rustCore = rustCore,
            transportPort = MultiChannelTransportAdapter(rustCore, discoveryDelayMs = 0L),
            notificationPort = bundle.notificationPort,
            clipboardPort = bundle.clipboardPort,
            virtualDisplayPort = bundle.virtualDisplayPort,
            virtualCameraMicPort = bundle.virtualCameraMicPort,
            audioSinkPort = LinuxPipeWireAudioSinkAdapter(),
            phoneEventPort = phone
        )
        val device = service.discoverAvailableDevices().first()
        service.connectToDevice(device, null, ConnectionDirection.DESKTOP_TO_MOBILE)
        service.toggleMusicMode(true, 1.3f, "Lo-fi para estudiar")
        service.toggleVirtualCameraAndMic(enableCamera = true, enableMicrophone = true, useBackCamera = false)
        service.toggleSecondaryDisplay(SecondaryDisplayConfig(enabled = true))
        phone.events(device, device.preferredTransport).take(160).toList().forEach { service.processPhoneEvent(it) }
        service.resolveNotification(NotificationResolutionAction.QuickReply("notif-1", "Sí, ya quedó lista"))
        service.pushClipboardText("https://www.ipn.mx/")
        service
    }

    /**
     * Genera una captura por sección y tema y verifica que cada archivo se haya escrito.
     */
    @Test
    fun capturasDeTodasLasSecciones() {
        outputDir.mkdirs()
        val service = servicioConSesionActiva()
        val controller = DesktopPanelController(service, CoroutineScope(Dispatchers.Unconfined), "GNU/LINUX")
        controller.refreshDevices()
        for (theme in listOf(ThemePreference.LIGHT, ThemePreference.DARK)) {
            controller.setThemePreference(theme)
            for (section in DesktopSection.entries) {
                controller.selectSection(section)
                val scene = ImageComposeScene(width = 1320, height = 860, density = Density(1f)) {
                    DesktopControlPanelScreen(controller)
                }
                scene.render(0L)
                val image = scene.render(1_000_000_000L)
                val png = image.encodeToData(EncodedImageFormat.PNG) ?: error("No se pudo codificar PNG")
                val file = File(outputDir, "${section.name.lowercase()}-${theme.name.lowercase()}.png")
                file.writeBytes(png.bytes)
                scene.close()
                assertTrue(file.length() > 10_000, "Captura vacía: ${file.name}")
            }
        }
    }
}
