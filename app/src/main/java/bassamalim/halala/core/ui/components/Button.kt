package bassamalim.halala.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaTheme
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Radius
import bassamalim.halala.core.ui.theme.Sizes
import bassamalim.halala.core.ui.theme.Spacing

/** The two kinds of button there are. */
enum class ButtonKind {
    /** Accent fill: the one action the app recommends. At most one per card or section. */
    Primary,

    /** Surface-2 fill with a line border: everything else, answer sets included. */
    Secondary
}

/**
 * A button: at least a touch target tall, radius-md, a sentence-case verb or direct answer.
 * [destructive] is the confirm of a destructive sheet: secondary, with state-over text.
 */
@Composable
fun HalalaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Secondary,
    destructive: Boolean = false,
    enabled: Boolean = true
) {
    val primary = kind == ButtonKind.Primary

    Box(
        modifier = modifier
            .heightIn(min = Sizes.touchTarget)
            .clip(Radius.md)
            .background(if (primary) HalalaColors.Accent else HalalaColors.Surface2)
            .then(if (primary) Modifier else Modifier.border(Sizes.border, HalalaColors.Line, Radius.md))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .padding(horizontal = Insets.button),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = HalalaType.BodyStrong.copy(
                fontWeight = if (primary) FontWeight(600) else FontWeight(500)
            ),
            textAlign = TextAlign.Center,
            color = when {
                primary -> HalalaColors.OnAccent
                destructive -> HalalaColors.StateOver
                else -> HalalaColors.Text
            }
        )
    }
}

/** A disabled control is dimmed rather than recoloured, so it keeps its kind. */
internal const val DISABLED_ALPHA = 0.4f

@Preview
@Composable
private fun ButtonPreview() = HalalaTheme {
    Row(Modifier.padding(Spacing.screen), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        HalalaButton(text = "Confirm all 214", onClick = {}, kind = ButtonKind.Primary)
        HalalaButton(text = "Change", onClick = {})
        HalalaButton(text = "Delete", onClick = {}, destructive = true)
    }
}
