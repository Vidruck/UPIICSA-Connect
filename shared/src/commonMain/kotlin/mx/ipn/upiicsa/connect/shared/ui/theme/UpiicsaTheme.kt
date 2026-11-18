package mx.ipn.upiicsa.connect.shared.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import mx.ipn.upiicsa.connect.shared.model.ThemePreference

/**
 * Contiene la paleta cromática oficial de la UPIICSA del Instituto Politécnico Nacional (IPN).
 */
object UpiicsaPalette {
    val VerdeUpiicsa = Color(0xFF006341)
    val VerdeUpiicsaOscuro = Color(0xFF00482F)
    val VerdeUpiicsaClaro = Color(0xFF4CD99B)
    val OroUpiicsa = Color(0xFFF2A900)
    val OroUpiicsaSuave = Color(0xFFFFD166)
    val GuindaIpn = Color(0xFF6C1D45)
    val GuindaIpnClaro = Color(0xFFE07A9F)

    val FondoClaro = Color(0xFFF5F8F6)
    val SuperficieClara = Color(0xFFFFFFFF)
    val SuperficieVarianteClara = Color(0xFFE4ECE7)
    val TextoOscuro = Color(0xFF101C17)
    val TextoSecundarioClaro = Color(0xFF42544C)

    val FondoOscuro = Color(0xFF0D1512)
    val SuperficieOscura = Color(0xFF14201B)
    val SuperficieVarianteOscura = Color(0xFF1E2F28)
    val TextoClaro = Color(0xFFEAF3EE)
    val TextoSecundarioOscuro = Color(0xFFB3C7BD)
}

/**
 * Esquema de colores Material 3 para Modo Claro basado en la identidad institucional UPIICSA-IPN.
 */
val UpiicsaLightColorScheme: ColorScheme = lightColorScheme(
    primary = UpiicsaPalette.VerdeUpiicsa,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC5EBDC),
    onPrimaryContainer = UpiicsaPalette.VerdeUpiicsaOscuro,
    secondary = UpiicsaPalette.OroUpiicsa,
    onSecondary = Color(0xFF231900),
    secondaryContainer = Color(0xFFFFEAB5),
    onSecondaryContainer = Color(0xFF3E2C00),
    tertiary = UpiicsaPalette.GuindaIpn,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF9D8E4),
    onTertiaryContainer = Color(0xFF3D0A24),
    background = UpiicsaPalette.FondoClaro,
    onBackground = UpiicsaPalette.TextoOscuro,
    surface = UpiicsaPalette.SuperficieClara,
    onSurface = UpiicsaPalette.TextoOscuro,
    surfaceVariant = UpiicsaPalette.SuperficieVarianteClara,
    onSurfaceVariant = UpiicsaPalette.TextoSecundarioClaro
)

/**
 * Esquema de colores Material 3 para Modo Oscuro garantizando contraste institucional UPIICSA-IPN.
 */
val UpiicsaDarkColorScheme: ColorScheme = darkColorScheme(
    primary = UpiicsaPalette.VerdeUpiicsaClaro,
    onPrimary = Color(0xFF003824),
    primaryContainer = UpiicsaPalette.VerdeUpiicsa,
    onPrimaryContainer = Color(0xFFD9F7E9),
    secondary = UpiicsaPalette.OroUpiicsaSuave,
    onSecondary = Color(0xFF362600),
    secondaryContainer = Color(0xFF5A4000),
    onSecondaryContainer = Color(0xFFFFEAB5),
    tertiary = UpiicsaPalette.GuindaIpnClaro,
    onTertiary = Color(0xFF470B29),
    tertiaryContainer = UpiicsaPalette.GuindaIpn,
    onTertiaryContainer = Color(0xFFFFD9E5),
    background = UpiicsaPalette.FondoOscuro,
    onBackground = UpiicsaPalette.TextoClaro,
    surface = UpiicsaPalette.SuperficieOscura,
    onSurface = UpiicsaPalette.TextoClaro,
    surfaceVariant = UpiicsaPalette.SuperficieVarianteOscura,
    onSurfaceVariant = UpiicsaPalette.TextoSecundarioOscuro
)

/**
 * Aplica el tema institucional UPIICSA-Connect adaptándose automáticamente al modo claro u oscuro del sistema.
 */
@Composable
fun UpiicsaConnectTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM_AUTO,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themePreference) {
        ThemePreference.SYSTEM_AUTO -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }

    val colorScheme = if (useDarkTheme) {
        UpiicsaDarkColorScheme
    } else {
        UpiicsaLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
