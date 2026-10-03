package bassamalim.halala.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import bassamalim.halala.R
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaNumbers
import bassamalim.halala.core.ui.theme.HalalaTheme
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Radius
import bassamalim.halala.core.ui.theme.Sizes
import bassamalim.halala.core.ui.theme.Spacing

/**
 * The default container: a surface fill lit faintly from above, a 1dp line border that catches
 * the light along its top, radius-lg, space-4 padding. It starts with a muted label when given
 * one. No shadows, no coloured borders, no coloured edges.
 */
@Composable
fun HalalaCard(
    modifier: Modifier = Modifier,
    label: String? = null,
    contentPadding: PaddingValues = PaddingValues(Spacing.card),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(Spacing.xs),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(Radius.lg)
            // Before the fill, so a press sinks the whole card, border and all.
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .background(CardFill)
            .border(Sizes.border, CardEdge, Radius.lg)
            .padding(contentPadding),
        verticalArrangement = verticalArrangement
    ) {
        if (label != null) CardLabel(label)
        content()
    }
}

/** A card's fill and border, lit from above. */
internal val CardFill = Brush.verticalGradient(listOf(HalalaColors.SurfaceLit, HalalaColors.Surface))
internal val CardEdge = Brush.verticalGradient(listOf(HalalaColors.LineLit, HalalaColors.Line))

/** The muted `label` a card or section starts with. */
@Composable
fun CardLabel(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = HalalaType.Label, color = HalalaColors.TextMuted, modifier = modifier)
}

/** A summary card: label, one amount-lg figure, and a caption under it. */
@Composable
fun SummaryCard(
    label: String,
    amount: String,
    modifier: Modifier = Modifier,
    amountColor: Color = HalalaColors.Text,
    currency: String? = null,
    caption: String? = null,
    captionColor: Color = HalalaColors.TextMuted,
    onClick: (() -> Unit)? = null
) {
    HalalaCard(modifier = modifier, label = label, onClick = onClick) {
        // A blank figure (still loading) is a shimmering bar; when it arrives its digits rise in.
        if (amount.isEmpty()) Text(
            text = "",
            style = HalalaNumbers.AmountLg,
            modifier = Modifier.fillMaxWidth(0.6f).clip(Radius.xs).shimmer()
        )
        else RollingAmount(text = amount, style = HalalaNumbers.AmountLg, color = amountColor, currency = currency)
        if (caption != null)
            Text(text = caption, style = HalalaType.Caption, color = captionColor)
    }
}

/** A card of settings-style rows, divided by 1dp lines rather than split into more cards. */
@Composable
fun ListCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    HalalaCard(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = Spacing.card),
        verticalArrangement = Arrangement.Top,
        content = content
    )
}

/**
 * One row in a [ListCard]: a title, an optional muted second line and, when tappable, a
 * chevron. [divider] draws the line above it; the first row in a card has none.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    divider: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Column(modifier.fillMaxWidth()) {
        if (divider) HorizontalDivider(thickness = Sizes.border, color = HalalaColors.Line)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Sizes.listRow)
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            leading?.invoke()

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
            ) {
                Text(text = title, style = HalalaType.BodyStrong)
                if (subtitle != null)
                    Text(text = subtitle, style = HalalaType.Caption, color = HalalaColors.TextMuted)
            }

            trailing?.invoke(this)

            if (onClick != null && trailing == null) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = HalalaColors.TextMuted,
                    modifier = Modifier.size(Sizes.iconSmall)
                )
            }
        }
    }
}

@Preview
@Composable
private fun CardPreview() = HalalaTheme {
    Column(Modifier.padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.card)) {
        SummaryCard(label = "Net worth", amount = "312,450", currency = "SAR", caption = "+2.1% this month", captionColor = HalalaColors.Income)
        ListCard {
            ListRow(title = "Accounts", subtitle = "7 accounts at 6 banks", onClick = {})
            ListRow(title = "Backup and export", subtitle = "CSV or JSON", divider = true, onClick = {})
        }
    }
}
