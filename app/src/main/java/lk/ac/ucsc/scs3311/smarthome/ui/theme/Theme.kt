package lk.ac.ucsc.scs3311.smarthome.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF00696D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF6FF6FC),
    onPrimaryContainer = Color(0xFF002021),
    secondary = Color(0xFF4A6363),
    tertiary = Color(0xFF4B607C),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    surface = Color(0xFFFAFDFC),
    onSurface = Color(0xFF191C1C),
    surfaceVariant = Color(0xFFDAE4E4),
    onSurfaceVariant = Color(0xFF3F4949),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4CD9E0),
    onPrimary = Color(0xFF003739),
    primaryContainer = Color(0xFF004F52),
    onPrimaryContainer = Color(0xFF6FF6FC),
    secondary = Color(0xFFB1CCCB),
    tertiary = Color(0xFFB3C8E8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    surface = Color(0xFF191C1C),
    onSurface = Color(0xFFE0E3E2),
    surfaceVariant = Color(0xFF3F4949),
    onSurfaceVariant = Color(0xFFBEC9C8),
)

/**
 * Status colours are defined once here and reused by every device card, so the
 * meaning of a colour never drifts between screens. They are always paired with
 * an icon and a text label — colour alone is neither accessible nor legible on
 * a lecture-theatre projector.
 */
object StatusColors {
    val on = Color(0xFF1B873F)
    val onContainer = Color(0xFFD7F4DF)
    val off = Color(0xFF6B7280)
    val offContainer = Color(0xFFE9EBEF)
    val error = Color(0xFFBA1A1A)
    val errorContainer = Color(0xFFFFDAD6)
    val disconnected = Color(0xFF8A6100)
    val disconnectedContainer = Color(0xFFFFE8B8)
    val warning = Color(0xFFB25E00)
}

@Composable
fun HomeSenseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** Material You wallpaper colours, available from Android 12. */
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
