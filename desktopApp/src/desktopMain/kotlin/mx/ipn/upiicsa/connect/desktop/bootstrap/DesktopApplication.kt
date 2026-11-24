package mx.ipn.upiicsa.connect.desktop.bootstrap

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.isTraySupported
import androidx.compose.ui.window.rememberWindowState
import mx.ipn.upiicsa.connect.desktop.adapters.os.OsAdapterFactory
import mx.ipn.upiicsa.connect.desktop.ui.DesktopControlPanelScreen
import mx.ipn.upiicsa.connect.desktop.ui.DesktopPanelController
import mx.ipn.upiicsa.connect.shared.ui.components.UpiicsaBrandPainter

/**
 * Raíz de composición del escritorio: único punto que conoce la fábrica de adaptadores y arranca la ventana principal.
 */
fun main() = application {
    val platform = remember { OsAdapterFactory.detectHostPlatform() }
    val service = remember { OsAdapterFactory.createDesktopService(platform) }
    val scope = rememberCoroutineScope()
    val controller = remember { DesktopPanelController(service, scope, platform.name.replace('_', '/')) }
    val icon = remember { UpiicsaBrandPainter() }
    var visible by remember { mutableStateOf(true) }

    LaunchedEffect(controller) { controller.start() }

    if (isTraySupported) {
        Tray(
            icon = icon,
            tooltip = "UPIICSA-Connect",
            onAction = { visible = true },
            menu = {
                Item("Mostrar panel", onClick = { visible = true })
                Item("Salir", onClick = ::exitApplication)
            }
        )
    }

    Window(
        onCloseRequest = { if (isTraySupported) visible = false else exitApplication() },
        visible = visible,
        title = "UPIICSA-Connect · ${platform.name.replace('_', '/')}",
        icon = icon,
        state = rememberWindowState(size = DpSize(1320.dp, 860.dp))
    ) {
        DesktopControlPanelScreen(controller)
    }
}
