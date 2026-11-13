package workout.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import workout.app.ui.WorkoutApp
import workout.app.ui.WorkoutTheme
import workout.app.ui.WorkoutViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WorkoutTheme {
                Surface(Modifier.fillMaxSize()) {
                    val model: WorkoutViewModel = viewModel()
                    WorkoutApp(model)
                }
            }
        }
    }
}
