package mx.ipn.upiicsa.connect.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import mx.ipn.upiicsa.connect.android.ui.AndroidConnectionPanelScreen
import mx.ipn.upiicsa.connect.android.viewmodel.AndroidConnectionViewModel

/**
 * Actividad única de la app: obtiene el ViewModel con ciclo de vida y monta la vista Compose principal.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: AndroidConnectionViewModel by viewModels { AndroidConnectionViewModel.Factory }

    /**
     * Crea la jerarquía Compose enlazando la vista con el ViewModel provisto por la fábrica MVVM.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AndroidConnectionPanelScreen(viewModel = viewModel)
        }
    }

    /**
     * Vuelve a consultar los permisos del sistema, por ejemplo al regresar de los ajustes de acceso a notificaciones.
     */
    override fun onResume() {
        super.onResume()
        viewModel.refreshSystemAccess()
    }
}
