package bassamalim.halala.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import bassamalim.halala.core.ui.PressIndication

/**
 * Halala is dark-only by design: no light scheme and no dynamic colour. Jade is the one signal,
 * and Material You would take it away.
 */
private val HalalaColorScheme = darkColorScheme(
    primary = HalalaColors.Accent,
    onPrimary = HalalaColors.OnAccent,
    primaryContainer = HalalaColors.Surface2,
    onPrimaryContainer = HalalaColors.Accent,
    secondary = HalalaColors.TextMuted,
    onSecondary = HalalaColors.Bg,
    secondaryContainer = HalalaColors.Surface2,
    onSecondaryContainer = HalalaColors.Text,
    tertiary = HalalaColors.Info,
    onTertiary = HalalaColors.OnAccent,
    background = HalalaColors.Bg,
    onBackground = HalalaColors.Text,
    surface = HalalaColors.Surface,
    onSurface = HalalaColors.Text,
    surfaceVariant = HalalaColors.Surface2,
    onSurfaceVariant = HalalaColors.TextMuted,
    surfaceContainerLowest = HalalaColors.Bg,
    surfaceContainerLow = HalalaColors.Surface,
    surfaceContainer = HalalaColors.Surface,
    surfaceContainerHigh = HalalaColors.Surface,
    surfaceContainerHighest = HalalaColors.Surface2,
    inverseSurface = HalalaColors.Text,
    inverseOnSurface = HalalaColors.Bg,
    outline = HalalaColors.Line,
    outlineVariant = HalalaColors.Line,
    error = HalalaColors.StateOver,
    onError = HalalaColors.OnAccent,
    errorContainer = HalalaColors.Surface2,
    onErrorContainer = HalalaColors.StateOver,
    scrim = HalalaColors.Scrim
)

@Composable
fun HalalaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HalalaColorScheme,
        typography = Typography,
        shapes = Shapes
    ) {
        // The whole app sits on one Surface so text with no explicit colour inherits Text.
        // Without it Compose's default content colour is black, invisible on Bg.
        Surface(color = HalalaColors.Bg, contentColor = HalalaColors.Text) {
            // Every clickable sinks and springs back rather than rippling (PressIndication).
            CompositionLocalProvider(LocalIndication provides PressIndication, content = content)
        }
    }
}
