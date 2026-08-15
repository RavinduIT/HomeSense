package lk.ac.ucsc.scs3311.smarthome.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import lk.ac.ucsc.scs3311.smarthome.notifications.SafetyNotifications
import lk.ac.ucsc.scs3311.smarthome.ui.navigation.HomeSenseNavHost
import lk.ac.ucsc.scs3311.smarthome.ui.theme.HomeSenseTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Channels must exist before the first notification is posted, and
        // before FCM delivers one while the app is in the background.
        SafetyNotifications.ensureChannels(this)

        setContent {
            HomeSenseTheme {
                RequestNotificationPermission()
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

/**
 * Asks for POST_NOTIFICATIONS on API 33+.
 *
 * Refusal is not treated as an error. The cut-off still happens server-side
 * and the alert still lands in the in-app alert centre; only the push is lost.
 * Nagging about it would be dishonest about how much the permission matters.
 */
@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { /* granted or not, the app works the same */ },
    )

    LaunchedEffect(Unit) {
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
