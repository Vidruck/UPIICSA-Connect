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
        fun fromEnvironment(): DesktopLinkMode = environmentOverride() ?: SIMULATED

        /**
         * Modo fijado por la propiedad o la variable de entorno (`real` o `simulado`); `null` si no se indicó.
         */
        fun environmentOverride(
            property: String? = System.getProperty(PROPERTY),
            environment: String? = System.getenv(ENV)
        ): DesktopLinkMode? = when ((property ?: environment).orEmpty().trim().lowercase()) {
            "real" -> REAL_TCP
            "simulado" -> SIMULATED
            else -> null
        }

        /**
         * Modo efectivo: la propiedad o variable de entorno tienen prioridad sobre la preferencia guardada en Ajustes.
         */
        fun resolve(override: DesktopLinkMode?, realLinkPreferred: Boolean): DesktopLinkMode =
            override ?: if (realLinkPreferred) REAL_TCP else SIMULATED
    }
}
