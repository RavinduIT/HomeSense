package lk.ac.ucsc.scs3311.smarthome.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import lk.ac.ucsc.scs3311.smarthome.ui.navigation.HomeSenseNavHost
import lk.ac.ucsc.scs3311.smarthome.ui.theme.HomeSenseTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            HomeSenseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    HomeSenseNavHost()
                }
            }
        }
    }
}
