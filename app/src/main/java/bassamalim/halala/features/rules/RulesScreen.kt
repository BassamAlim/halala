package bassamalim.halala.features.rules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.halala.R
import bassamalim.halala.core.enums.RuleSource
import bassamalim.halala.core.ui.components.ChoiceSheet
import bassamalim.halala.core.ui.components.DISABLED_ALPHA
import bassamalim.halala.core.ui.components.HalalaButton
import bassamalim.halala.core.ui.components.HalalaSheet
import bassamalim.halala.core.ui.components.SearchField
import bassamalim.halala.core.ui.components.TopBar
import bassamalim.halala.core.ui.dayText
import bassamalim.halala.core.ui.ruleSentence
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Radius
import bassamalim.halala.core.ui.theme.Sizes
import bassamalim.halala.core.ui.theme.Spacing

/**
 * Rules, from the Rules board: every rule in plain words with who made it and how often it was
 * used, searchable; a rule opens to change its category, turn it off, or delete it. Writing a
 * rule by hand (the board's +) and the Learned / Yours / AI filter arrive once there is more
 * than one kind of rule.
 */
@Composable
fun RulesScreen(viewModel: RulesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screen)
            .padding(top = Insets.screenTop),
        verticalArrangement = Arrangement.spacedBy(Spacing.card)
    ) {
        TopBar(title = stringResource(R.string.rules), onBack = viewModel::onBackClick)

        if (state.isLoading) return@Column

        SearchField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            placeholder = stringResource(R.string.rules_search)
        )

        LazyColumn(contentPadding = PaddingValues(bottom = Spacing.section)) {
            itemsIndexed(state.rules, key = { _, rule -> rule.id }) { index, rule ->
                RuleRow(rule, divider = index > 0, onClick = { viewModel.onRuleClick(rule.id) })
            }

            item {
                Text(
                    text = stringResource(
                        if (state.rules.isEmpty() && state.query.isEmpty()) R.string.rules_empty
                        else R.string.rules_footer
                    ),
                    style = HalalaType.Caption,
                    color = HalalaColors.TextMuted,
                    modifier = Modifier.padding(top = Spacing.card)
                )
            }
        }
    }

    val selected = state.selected ?: return

    if (state.isPickingCategory) {
        ChoiceSheet(
            title = selected.merchant,
            options = state.categories,
            selected = state.categories.firstOrNull { it.id == selected.categoryId },
            label = { it.name },
            onPick = viewModel::onCategoryPick,
            onDismiss = viewModel::onSheetDismiss
        )
    } else {
        HalalaSheet(onDismiss = viewModel::onSheetDismiss) {
            Text(
                text = ruleSentence(selected.merchant, selected.category, selected.expenseType),
                style = HalalaType.Title
            )
            Text(text = ruleMeta(selected), style = HalalaType.Body, color = HalalaColors.TextMuted)

            Column(
                modifier = Modifier.padding(top = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                HalalaButton(
                    text = stringResource(R.string.rule_change_category),
                    onClick = viewModel::onChangeCategoryClick,
                    modifier = Modifier.fillMaxWidth()
                )
                HalalaButton(
                    text = stringResource(if (selected.enabled) R.string.rule_turn_off else R.string.rule_turn_on),
                    onClick = viewModel::onToggleClick,
                    modifier = Modifier.fillMaxWidth()
                )
                HalalaButton(
                    text = stringResource(R.string.delete),
                    onClick = viewModel::onDeleteClick,
                    destructive = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** The rule in plain words over who made it and what it has done; one that is off is dimmed. */
@Composable
private fun RuleRow(rule: RuleItem, divider: Boolean, onClick: () -> Unit) {
    Column {
        if (divider) HorizontalDivider(thickness = Sizes.border, color = HalalaColors.Line)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Sizes.touchTarget)
                .clip(Radius.sm)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = Insets.row)
                .alpha(if (rule.enabled) 1f else DISABLED_ALPHA),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(
                    text = ruleSentence(rule.merchant, rule.category, rule.expenseType),
                    style = HalalaType.Body
                )
                Text(text = ruleMeta(rule), style = HalalaType.Caption, color = HalalaColors.TextMuted)
            }
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = HalalaColors.TextMuted,
                modifier = Modifier.size(Sizes.iconSmall)
            )
        }
    }
}

/** "Learned from you · used 214 times · last Today", or "… · Off". */
@Composable
private fun ruleMeta(rule: RuleItem): String {
    val source = stringResource(
        when (rule.source) {
            RuleSource.MANUAL -> R.string.rule_source_manual
            RuleSource.LEARNED -> R.string.rule_source_learned
            RuleSource.AI -> R.string.rule_source_ai
        }
    )
    val used = stringResource(
        R.string.meta_pair,
        source,
        pluralStringResource(R.plurals.rule_used, rule.hits, rule.hits)
    )
    return when {
        !rule.enabled -> stringResource(R.string.meta_pair, used, stringResource(R.string.rule_off))
        rule.lastHit != null ->
            stringResource(R.string.meta_pair, used, stringResource(R.string.rule_last, dayText(rule.lastHit)))
        else -> used
    }
}
