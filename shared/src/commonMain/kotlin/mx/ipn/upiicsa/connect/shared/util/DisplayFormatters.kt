package mx.ipn.upiicsa.connect.shared.util

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Funciones de formato multiplataforma (sin `String.format`, no disponible en código común) para las interfaces.
 */
object DisplayFormatters {

    /**
     * Formatea un número decimal con la cantidad fija de decimales indicada (por ejemplo, 1.15 -> "1.15").
     */
    fun decimal(value: Float, digits: Int = 2): String {
        var factor = 1L
        repeat(digits) { factor *= 10 }
        val scaled = (abs(value.toDouble()) * factor).roundToLong()
        val sign = if (value < 0 && scaled != 0L) "-" else ""
        val integerPart = scaled / factor
        if (digits == 0) {
            return "$sign$integerPart"
        }
        val fraction = (scaled % factor).toString().padStart(digits, '0')
        return "$sign$integerPart.$fraction"
    }

    /**
     * Formatea un nivel normalizado 0..1 como porcentaje entero (por ejemplo, 0.734 -> "73 %").
     */
    fun percent(level: Float): String = "${(level.coerceIn(0f, 1f) * 100).roundToLong()} %"

    /**
     * Formatea un PIN de 6 dígitos como dos grupos de tres (por ejemplo, 48213 -> "048 213").
     */
    fun pairingPin(pin: Int): String {
        val digits = (pin % 1_000_000).toString().padStart(6, '0')
        return "${digits.substring(0, 3)} ${digits.substring(3)}"
    }

    /**
     * Formatea una cantidad grande de forma compacta (por ejemplo, 15300 -> "15.3 k").
     */
    fun compactCount(value: Long): String = when {
        value >= 1_000_000 -> "${decimal(value / 1_000_000f, 1)} M"
        value >= 1_000 -> "${decimal(value / 1_000f, 1)} k"
        else -> value.toString()
    }
}
