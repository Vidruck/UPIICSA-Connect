package mx.ipn.upiicsa.connect.android.model

import mx.ipn.upiicsa.connect.shared.protocol.RustCoreBridgeContract

/**
 * Repositorio de la capa Model (MVVM) que deriva el PIN de emparejamiento compartido con el ordenador.
 */
class AndroidPairingRepository(
    private val rustCore: RustCoreBridgeContract,
    private val clock: () -> Long = System::currentTimeMillis
) {

    /**
     * Identificador estable de este teléfono dentro del protocolo de emparejamiento.
     */
    val mobileDeviceId: String
        // [DUMMY-PRUEBAS]: Identificador fijo en lugar de uno persistido en almacenamiento cifrado (Keystore) por instalación.
        get() = "android-upiicsa-movil"

    /**
     * Calcula el PIN de 6 dígitos que debe coincidir con el mostrado por el equipo de escritorio.
     */
    fun pairingPinFor(desktopId: String): Int =
        rustCore.derivePairingPin(desktopId, mobileDeviceId, clock() / PIN_ROTATION_MS)

    private companion object {
        const val PIN_ROTATION_MS = 30_000L
    }
}
