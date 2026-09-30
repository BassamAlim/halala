package bassamalim.halala.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import bassamalim.halala.R
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaNumbers
import bassamalim.halala.core.ui.theme.HalalaTheme
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Radius
import bassamalim.halala.core.ui.theme.Sizes
import bassamalim.halala.core.ui.theme.Spacing

/**
 * The 44dp search field: surface, line border, radius-md, a muted glyph. Focus shows the
 * focus ring. (The placeholder invites a question; handing it to the assistant comes later.)
 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget)
            .clip(Radius.md)
            .background(HalalaColors.Surface)
            .border(
                width = if (focused) FOCUS_RING else Sizes.border,
                color = if (focused) HalalaColors.FocusRing else HalalaColors.Line,
                shape = Radius.md
            )
            .padding(horizontal = Insets.field),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = null,
            tint = HalalaColors.TextMuted,
            modifier = Modifier.size(Sizes.iconSmall)
        )

        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty())
                Text(text = placeholder, style = HalalaType.Body, color = HalalaColors.TextMuted, maxLines = 1)

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                interactionSource = interaction,
                textStyle = HalalaType.Body.merge(TextStyle(color = HalalaColors.Text)),
                cursorBrush = SolidColor(HalalaColors.Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * The app's text input, from the onboarding board: surface-2, line border, radius-sm, at least a
 * touch target tall. [numeric] sets it in Plex Mono with a number keyboard, for amounts and
 * digits.
 */
@Composable
fun HalalaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    numeric: Boolean = false,
    keyboardType: KeyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text,
    singleLine: Boolean = true,
    isError: Boolean = false,
    textStyle: TextStyle = if (numeric) HalalaNumbers.Amount else HalalaType.Body
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget)
            .clip(Radius.sm)
            .background(HalalaColors.Surface2)
            .border(
                width = if (focused || isError) FOCUS_RING else Sizes.border,
                color = when {
                    isError -> HalalaColors.StateOver
                    focused -> HalalaColors.FocusRing
                    else -> HalalaColors.Line
                },
                shape = Radius.sm
            )
            .padding(horizontal = Insets.field, vertical = Spacing.sm),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty() && placeholder.isNotEmpty())
            Text(text = placeholder, style = textStyle, color = HalalaColors.TextMuted, maxLines = 1)

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            interactionSource = interaction,
            textStyle = textStyle.merge(TextStyle(color = HalalaColors.Text)),
            cursorBrush = SolidColor(HalalaColors.Accent),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** The 2dp focus ring from the tokens. */
private val FOCUS_RING = Spacing.xxs

@Preview
@Composable
private fun SearchFieldPreview() = HalalaTheme {
    SearchField(
        value = "",
        onValueChange = {},
        placeholder = "Search, or ask “coffee since June”",
        modifier = Modifier.padding(Spacing.screen)
    )
}
