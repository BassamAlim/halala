package bassamalim.halala.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import bassamalim.halala.R
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Radius
import bassamalim.halala.core.ui.theme.Sizes
import bassamalim.halala.core.ui.theme.Spacing

/**
 * Where the map would be, drawn as a quiet street grid, with why there is no map and the one
 * thing that fixes it.
 */
@Composable
fun MapPlaceholder(message: String, action: String?, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.clip(Radius.lg).background(HalalaColors.Surface), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val street = Sizes.border.toPx() * 6
            val lane = Sizes.border.toPx() * 2
            val line = HalalaColors.Line
            // A few avenues and a looser grid of lanes, like a city seen from above.
            for (i in 1..5) {
                val x = size.width * i / 6f + (if (i % 2 == 0) size.width * 0.03f else 0f)
                drawLine(line, Offset(x, 0f), Offset(x, size.height), strokeWidth = if (i == 3) street else lane)
            }
            for (i in 1..4) {
                val y = size.height * i / 5f
                drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = if (i == 2) street else lane)
            }
            drawLine(line, Offset(0f, size.height * 0.9f), Offset(size.width, size.height * 0.15f), strokeWidth = street, cap = StrokeCap.Round)
        }
        Column(
            modifier = Modifier
                .clip(Radius.md)
                .background(HalalaColors.Bg)
                .padding(Spacing.card),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Icon(painter = painterResource(R.drawable.ic_pin), contentDescription = null, tint = HalalaColors.TextMuted, modifier = Modifier.padding(top = Spacing.xs))
            Text(text = message, style = HalalaType.Body, color = HalalaColors.TextMuted, textAlign = TextAlign.Center)
            if (action != null) HalalaButton(text = action, onClick = onAction, kind = ButtonKind.Primary)
        }
    }
}
