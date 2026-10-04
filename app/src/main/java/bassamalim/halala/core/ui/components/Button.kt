package bassamalim.halala.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
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
 * [destructive] is the confirm of a destructive sheet: secondary, with state-over text. An
 * [icon] (a drawable glyph) leads the text, in jade on a secondary button.
 */
@Composable
fun HalalaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Secondary,
    destructive: Boolean = false,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null
) {
    val primary = kind == ButtonKind.Primary

    Box(
        modifier = modifier
            .heightIn(min = Sizes.touchTarget)
            .clip(Radius.md)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            // Before the fill, so a press sinks the whole button.
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .then(if (primary) Modifier.background(PrimaryFill) else Modifier.background(HalalaColors.Surface2))
            .border(Sizes.border, if (primary) PrimaryEdge else SolidColor(HalalaColors.Line), Radius.md)
            .padding(horizontal = Insets.button),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            icon?.let {
                Icon(
                    painter = painterResource(it),
                    contentDescription = null,
                    tint = if (primary) HalalaColors.OnAccent else HalalaColors.Accent,
                    modifier = Modifier.size(Sizes.iconSmall)
                )
            }
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
}

/** Jade with a sheen across its top: the one lit thing in a section. Secondary is flat. */
private val PrimaryFill = Brush.verticalGradient(listOf(lerp(HalalaColors.Accent, Color.White, 0.12f), HalalaColors.Accent))
private val PrimaryEdge = Brush.verticalGradient(listOf(HalalaColors.Sheen, Color.Transparent))

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
