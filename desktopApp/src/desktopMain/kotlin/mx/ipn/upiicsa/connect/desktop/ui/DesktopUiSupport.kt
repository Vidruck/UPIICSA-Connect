package mx.ipn.upiicsa.connect.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Utilidades de formato de presentación exclusivas de la JVM de escritorio.
 */
object DesktopTimeFormat {

    private val formatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

    /**
     * Convierte una marca de tiempo en milisegundos a la hora local "HH:mm:ss".
     */
    fun clock(epochMillis: Long): String = formatter.format(Instant.ofEpochMilli(epochMillis))
}

/**
 * Contenedor desplazable común de cada pantalla con márgenes y separación uniformes.
 */
@Composable
fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        content = content
    )
}
