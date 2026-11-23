package mx.ipn.upiicsa.connect.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.ipn.upiicsa.connect.android.ui.AndroidConnectionPanelScreen
import mx.ipn.upiicsa.connect.android.viewmodel.AndroidConnectionViewModel

/**
 * Actividad única de la app: obtiene el ViewModel con ciclo de vida y monta la vista Compose principal.
 */
class MainActivity : ComponentActivity() {

    /**
     * Crea la jerarquía Compose enlazando la vista con el ViewModel provisto por la fábrica MVVM.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: AndroidConnectionViewModel = viewModel(factory = AndroidConnectionViewModel.Factory)
            AndroidConnectionPanelScreen(viewModel = viewModel)
        }
    }
}
