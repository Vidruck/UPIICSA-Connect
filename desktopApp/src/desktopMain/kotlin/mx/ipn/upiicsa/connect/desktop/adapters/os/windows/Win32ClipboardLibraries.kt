package mx.ipn.upiicsa.connect.desktop.adapters.os.windows

import com.sun.jna.Library
import com.sun.jna.Pointer

/**
 * Funciones de user32.dll para el portapapeles de Windows (vinculadas por JNA).
 */
@Suppress("FunctionName")
internal interface User32ClipboardLibrary : Library {
    /**
     * Abre el portapapeles para la tarea actual (`hWndNewOwner` nulo).
     */
    fun OpenClipboard(hWndNewOwner: Pointer?): Boolean

    /**
     * Cierra el portapapeles abierto por la tarea actual.
     */
    fun CloseClipboard(): Boolean

    /**
     * Vacía el portapapeles y toma su propiedad.
     */
    fun EmptyClipboard(): Boolean

    /**
     * Indica si el portapapeles contiene datos en el formato indicado.
     */
    fun IsClipboardFormatAvailable(format: Int): Boolean

    /**
     * Retorna el bloque de memoria global con los datos del formato indicado.
     */
    fun GetClipboardData(format: Int): Pointer?

    /**
     * Entrega a Windows un bloque de memoria global con datos del formato indicado.
     */
    fun SetClipboardData(format: Int, hMem: Pointer?): Pointer?
}

/**
 * Funciones de kernel32.dll para la memoria global que exige el portapapeles (vinculadas por JNA).
 */
@Suppress("FunctionName")
internal interface Kernel32GlobalMemoryLibrary : Library {
    /**
     * Reserva un bloque de memoria global.
     */
    fun GlobalAlloc(flags: Int, bytes: Long): Pointer?

    /**
     * Bloquea un bloque de memoria global y retorna su dirección.
     */
    fun GlobalLock(hMem: Pointer?): Pointer?

    /**
     * Desbloquea un bloque de memoria global.
     */
    fun GlobalUnlock(hMem: Pointer?): Boolean

    /**
     * Libera un bloque de memoria global que no fue entregado al portapapeles.
     */
    fun GlobalFree(hMem: Pointer?): Pointer?
}
