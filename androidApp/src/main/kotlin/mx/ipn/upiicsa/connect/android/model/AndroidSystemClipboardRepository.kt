package mx.ipn.upiicsa.connect.android.model

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Repositorio (capa Model) del portapapeles real de Android (`ClipboardManager`). Desde Android 10 solo puede leerse
 * mientras la app está en primer plano, por lo que los cambios hechos con la app en segundo plano no se detectan.
 */
class AndroidSystemClipboardRepository(private val context: Context) {

    private val clipboard: ClipboardManager? = context.getSystemService(ClipboardManager::class.java)

    /**
     * Copia `text` al portapapeles del teléfono.
     */
    fun write(text: String) {
        clipboard?.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, text))
    }

    /**
     * Lee el texto vigente; retorna `null` si no hay texto o Android lo impide (app en segundo plano).
     */
    fun read(): String? = runCatching {
        clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
    }.getOrNull()

    /**
     * Emite el texto copiado cada vez que cambia el portapapeles mientras la app está en primer plano.
     */
    fun changes(): Flow<String> = callbackFlow {
        val listener = ClipboardManager.OnPrimaryClipChangedListener {
            read()?.takeIf { it.isNotBlank() }?.let { trySend(it) }
        }
        clipboard?.addPrimaryClipChangedListener(listener)
        awaitClose { clipboard?.removePrimaryClipChangedListener(listener) }
    }

    private companion object {
        const val CLIP_LABEL = "UPIICSA-Connect"
    }
}
