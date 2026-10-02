package bassamalim.halala.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import bassamalim.halala.R
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaTheme
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Radius
import bassamalim.halala.core.ui.theme.Sizes
import bassamalim.halala.core.ui.theme.Spacing

/**
 * The header of every sub-screen: a 44dp back button, the title, and at most one text action
 * on the right in accent. Top-level tabs use [ScreenTitle] instead and keep the bottom nav.
 */
@Composable
fun TopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    actionEnabled: Boolean = true,
    onAction: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Box(
            modifier = Modifier
                .offset(x = -Insets.backNudge)
                .size(Sizes.touchTarget)
                .clip(Radius.pill)
                .clickable(role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_back),
                contentDescription = stringResource(R.string.back),
                tint = HalalaColors.Text,
                modifier = Modifier.size(Sizes.icon)
            )
        }

        Text(
            text = title,
            style = HalalaType.Title,
            modifier = Modifier
                .weight(1f)
                .offset(x = -Insets.backNudge)
                .semantics { heading() }
        )

        if (actionLabel != null) {
            Box(
                modifier = Modifier
                    .heightIn(min = Sizes.touchTarget)
                    .clip(Radius.sm)
                    .clickable(enabled = actionEnabled, role = Role.Button, onClick = onAction)
                    .alpha(if (actionEnabled) 1f else DISABLED_ALPHA)
                    .padding(horizontal = Spacing.xs),
                contentAlignment = Alignment.Center
            ) {
                Text(text = actionLabel, style = HalalaType.Body, color = HalalaColors.Accent)
            }
        }
    }
}

/** A top-level tab's title, 26sp, with room for one thing beside it. */
@Composable
fun ScreenTitle(
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = text, style = HalalaType.ScreenTitle, modifier = Modifier.semantics { heading() })
        trailing?.invoke()
    }
}

@Preview
@Composable
private fun TopBarPreview() = HalalaTheme {
    TopBar(
        title = "Transaction",
        onBack = {},
        actionLabel = "Edit",
        modifier = Modifier.padding(horizontal = Spacing.screen)
    )
}
