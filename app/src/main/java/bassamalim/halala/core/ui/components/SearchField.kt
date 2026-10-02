package bassamalim.halala.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.PasswordVisualTransformation

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
    val focusManager = LocalFocusManager.current

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
                // The list filters as you type, so Search only puts the keyboard away.
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (value.isNotEmpty()) {
            Box(
                modifier = Modifier
                    // Into the field's end padding, so the glyph sits nearer the edge.
                    .offset(x = Insets.field / 2)
                    .size(Sizes.touchTarget)
                    .clip(Radius.pill)
                    .clickable(role = Role.Button, onClick = { onValueChange("") }),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.clear_search),
                    tint = HalalaColors.TextMuted,
                    modifier = Modifier.size(Sizes.iconSmall)
                )
            }
        }
    }
}

/**
 * The app's text input, from the onboarding board: surface-2, line border, radius-sm, at least a
 * touch target tall. [numeric] sets it in Plex Mono with a number keyboard, for amounts and
 * digits. [secret] hides what is typed and keeps the keyboard from learning it, for keys.
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
    textStyle: TextStyle = if (numeric) HalalaNumbers.Amount else HalalaType.Body,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    /** Next moves to the following field; the last single-line field in a form passes Done. */
    imeAction: ImeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
    focusRequester: FocusRequester? = null,
    secret: Boolean = false,
    /** What the keyboard's action key does, when not just moving on (Send, in the assistant). */
    onImeAction: (() -> Unit)? = null
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
            keyboardOptions = KeyboardOptions(
                capitalization = capitalization,
                autoCorrectEnabled = !secret,
                keyboardType = if (secret) KeyboardType.Password else keyboardType,
                imeAction = imeAction
            ),
            keyboardActions = if (onImeAction != null) KeyboardActions(onAny = { onImeAction() }) else KeyboardActions.Default,
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
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
