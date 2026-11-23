package mx.ipn.upiicsa.connect.shared.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * Colores semánticos complementarios a Material 3 (éxito, advertencia, información y estados de enlace).
 */
@Immutable
data class UpiicsaExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val linkUsb: Color,
    val linkWifi: Color,
    val linkBluetooth: Color,
    val meterLow: Color,
    val meterMid: Color,
    val meterHigh: Color,
    val previewCanvas: Color,
    val previewWindow: Color,
    val isDark: Boolean
)

/**
 * Colores semánticos para Modo Claro.
 */
val UpiicsaLightExtendedColors = UpiicsaExtendedColors(
    success = Color(0xFF1B7F4B),
    onSuccess = Color.White,
    successContainer = Color(0xFFCDEFD9),
    warning = Color(0xFFB37800),
    onWarning = Color.White,
    warningContainer = Color(0xFFFFE8B0),
    info = Color(0xFF1F5FA8),
    infoContainer = Color(0xFFD6E4F7),
    linkUsb = UpiicsaPalette.VerdeUpiicsa,
    linkWifi = Color(0xFF1F5FA8),
    linkBluetooth = Color(0xFF5B4BB7),
    meterLow = UpiicsaPalette.VerdeUpiicsa,
    meterMid = UpiicsaPalette.OroUpiicsa,
    meterHigh = UpiicsaPalette.GuindaIpn,
    previewCanvas = Color(0xFF173B2E),
    previewWindow = Color(0xFFF5F8F6),
    isDark = false
)

/**
 * Colores semánticos para Modo Oscuro con contraste reforzado.
 */
val UpiicsaDarkExtendedColors = UpiicsaExtendedColors(
    success = Color(0xFF6EE7A8),
    onSuccess = Color(0xFF00391F),
    successContainer = Color(0xFF0F4D2F),
    warning = Color(0xFFFFC95C),
    onWarning = Color(0xFF3D2A00),
    warningContainer = Color(0xFF5A4000),
    info = Color(0xFF9CC4F5),
    infoContainer = Color(0xFF16375E),
    linkUsb = UpiicsaPalette.VerdeUpiicsaClaro,
    linkWifi = Color(0xFF9CC4F5),
    linkBluetooth = Color(0xFFB9AEF5),
    meterLow = UpiicsaPalette.VerdeUpiicsaClaro,
    meterMid = UpiicsaPalette.OroUpiicsaSuave,
    meterHigh = UpiicsaPalette.GuindaIpnClaro,
    previewCanvas = Color(0xFF07100C),
    previewWindow = Color(0xFF1E2F28),
    isDark = true
)

/**
 * Escala tipográfica institucional con jerarquía clara para paneles de control.
 */
val UpiicsaTypography: Typography = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
        labelSmall = base.labelSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp)
    )
}

/**
 * Estilo monoespaciado para PIN, identificadores de trama y métricas técnicas.
 */
val UpiicsaMonoStyle = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)

/**
 * Geometría de esquinas redondeadas del sistema visual UPIICSA-Connect.
 */
val UpiicsaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * Proveedor de composición de los colores semánticos extendidos.
 */
val LocalUpiicsaExtendedColors = staticCompositionLocalOf { UpiicsaLightExtendedColors }

/**
 * Punto de acceso a los tokens extendidos del tema dentro de cualquier Composable.
 */
object UpiicsaThemeTokens {

    /**
     * Colores semánticos activos según el modo claro u oscuro aplicado.
     */
    val extendedColors: UpiicsaExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalUpiicsaExtendedColors.current
}

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

    val extendedColors = if (useDarkTheme) {
        UpiicsaDarkExtendedColors
    } else {
        UpiicsaLightExtendedColors
    }

    CompositionLocalProvider(LocalUpiicsaExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = UpiicsaTypography,
            shapes = UpiicsaShapes,
            content = content
        )
    }
}
