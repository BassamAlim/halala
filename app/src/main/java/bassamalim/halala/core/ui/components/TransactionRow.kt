package bassamalim.halala.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import bassamalim.halala.R
import bassamalim.halala.core.enums.AmountTone
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaNumbers
import bassamalim.halala.core.ui.theme.HalalaTheme
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Radius
import bassamalim.halala.core.ui.theme.Sizes
import bassamalim.halala.core.ui.theme.Spacing

/**
 * One transaction in a list: avatar, title and caption metadata, and the amount right-aligned.
 * The consumer provides the already-signed [amount] string. [divider] draws the 1dp line above
 * it; the first row in a group has none.
 */
@Composable
fun TransactionRow(
    title: String,
    meta: String,
    amount: String,
    tone: AmountTone,
    initial: String,
    modifier: Modifier = Modifier,
    autoLabel: String? = null,
    divider: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Column(modifier.fillMaxWidth()) {
        if (divider) HorizontalDivider(thickness = Sizes.border, color = HalalaColors.Line)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .padding(vertical = Insets.row),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Avatar(initial = initial, tone = tone)

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Text(
                        text = title,
                        style = HalalaType.BodyStrong,
                        color = if (tone == AmountTone.Internal) HalalaColors.TextMuted else HalalaColors.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (autoLabel != null) AutoBadge(autoLabel)
                }

                Text(
                    text = meta,
                    style = HalalaType.Caption,
                    color = HalalaColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = amount,
                style = HalalaNumbers.Amount,
                color = when (tone) {
                    AmountTone.Spending -> HalalaColors.Text
                    AmountTone.Income -> HalalaColors.Income
                    AmountTone.Internal -> HalalaColors.TextMuted
                }
            )
        }
    }
}

/**
 * A 36dp merchant or person initial on surface-2. Income puts the letter in the income colour;
 * a move between your accounts is a dashed outline around the swap glyph.
 */
@Composable
fun Avatar(initial: String, modifier: Modifier = Modifier, tone: AmountTone = AmountTone.Spending) {
    if (tone == AmountTone.Internal) {
        Box(
            modifier = modifier
                .size(Sizes.avatar)
                .drawBehind {
                    drawRoundRect(
                        color = HalalaColors.Line,
                        cornerRadius = CornerRadius(AVATAR_RADIUS.toPx()),
                        style = Stroke(
                            width = Sizes.border.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH.toPx(), DASH.toPx()))
                        )
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_swap),
                contentDescription = null,
                tint = HalalaColors.TextMuted,
                modifier = Modifier.size(Sizes.iconSmall)
            )
        }
        return
    }

    Box(
        modifier = modifier
            .size(Sizes.avatar)
            .clip(Radius.sm)
            .background(HalalaColors.Surface2),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            style = HalalaType.Label.copy(fontWeight = FontWeight(600)),
            color = if (tone == AmountTone.Income) HalalaColors.Income else HalalaColors.Text
        )
    }
}

private val AVATAR_RADIUS = Spacing.md
private val DASH = Spacing.xs

/** A muted caption over a group of rows: "Today", "Yesterday", "Sat 27 Sep". */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = HalalaType.Caption,
        color = HalalaColors.TextMuted,
        modifier = modifier.padding(top = Insets.groupLabelTop, bottom = Insets.groupLabelBottom)
    )
}

@Preview
@Composable
private fun TransactionRowPreview() = HalalaTheme {
    Column(Modifier.padding(Spacing.screen)) {
        TransactionRow("Panda", "Groceries · Al Rajhi – Salary", "−214.50", AmountTone.Spending, "P", autoLabel = "Auto")
        TransactionRow("Salary → Awaeed", "Between your accounts · not spending", "5,000.00", AmountTone.Internal, "", divider = true)
        TransactionRow("Salary", "Al Rajhi – Salary", "+18,000.00", AmountTone.Income, "S", divider = true)
    }
}
