package mx.ipn.upiicsa.connect.desktop.adapters.transport

/**
 * Modo de enlace con el teléfono elegido al arrancar: simulador integrado o enlace real por TCP (`adb reverse`).
 */
enum class DesktopLinkMode(val displayLabel: String) {
    SIMULATED("Simulado (teléfono virtual)"),
    REAL_TCP("Real (TCP por cable USB / adb reverse)");

    companion object {
        /**
         * Propiedad del sistema que selecciona el modo (`real` o `simulado`).
         */
        const val PROPERTY = "upiicsa.enlace"

        /**
         * Variable de entorno alternativa a [PROPERTY].
         */
        const val ENV = "UPIICSA_ENLACE"

        /**
         * Lee el modo de la propiedad o variable de entorno; el simulador es el valor por omisión.
         */
        fun fromEnvironment(): DesktopLinkMode {
            val value = (System.getProperty(PROPERTY) ?: System.getenv(ENV)).orEmpty().trim().lowercase()
            return if (value == "real") REAL_TCP else SIMULATED
        }
    }
}
