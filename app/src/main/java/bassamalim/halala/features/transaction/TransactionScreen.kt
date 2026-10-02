package bassamalim.halala.features.transaction

import bassamalim.halala.core.ui.components.currencyInlineContent
import bassamalim.halala.core.ui.components.appendCurrency
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.halala.R
import bassamalim.halala.core.enums.AmountTone
import bassamalim.halala.core.enums.ExpenseType
import bassamalim.halala.core.enums.TransactionSource
import bassamalim.halala.core.ui.components.Avatar
import bassamalim.halala.core.ui.components.CardLabel
import bassamalim.halala.core.ui.components.ChipStyle
import bassamalim.halala.core.ui.components.ChoiceSheet
import bassamalim.halala.core.ui.components.ConfirmSheet
import bassamalim.halala.core.ui.components.HalalaButton
import bassamalim.halala.core.ui.components.HalalaCard
import bassamalim.halala.core.ui.components.HalalaChip
import bassamalim.halala.core.ui.components.TopBar
import bassamalim.halala.core.ui.expenseTypeLabel
import bassamalim.halala.core.ui.kindLabel
import bassamalim.halala.core.ui.ruleSentence
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaNumbers
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Radius
import bassamalim.halala.core.ui.theme.Sizes
import bassamalim.halala.core.ui.theme.Spacing

/**
 * One transaction, from the Transaction detail board: the figure, when and where, what it is,
 * its category and type, the rule that filed it, and how it got here. Tags, location, the raw
 * SMS and loan/split actions arrive with the phases that fill them.
 */
@Composable
fun TransactionScreen(viewModel: TransactionViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen)
            .padding(top = Insets.screenTop, bottom = Spacing.section),
        verticalArrangement = Arrangement.spacedBy(Spacing.card)
    ) {
        TopBar(
            title = stringResource(R.string.transaction),
            onBack = viewModel::onBackClick,
            actionLabel = stringResource(R.string.edit),
            actionEnabled = !state.isLoading,
            onAction = viewModel::onEditClick
        )

        if (state.isLoading) return@Column

        val title = state.title.ifBlank { kindLabel(state.kind) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.xs, bottom = Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Avatar(initial = state.initial, tone = state.tone)
            Text(text = title, style = HalalaType.Title, textAlign = TextAlign.Center)
            Text(
                text = buildAnnotatedString {
                    append(state.amount)
                    withStyle(SpanStyle(fontSize = HalalaType.Title.fontSize, letterSpacing = 0.em, color = HalalaColors.TextMuted)) {
                        appendCurrency(state.currency)
                    }
                },
                style = HalalaNumbers.AmountXl,
                inlineContent = currencyInlineContent(HalalaColors.TextMuted),
                maxLines = 1,
                // A long figure shrinks to fit the width rather than wrapping.
                autoSize = TextAutoSize.StepBased(
                    minFontSize = HalalaNumbers.AmountLg.fontSize,
                    maxFontSize = HalalaNumbers.AmountXl.fontSize
                ),
                color = when (state.tone) {
                    AmountTone.Income -> HalalaColors.Income
                    AmountTone.Internal -> HalalaColors.TextMuted
                    AmountTone.Spending -> HalalaColors.Text
                }
            )
            Text(
                text = if (state.isMove) state.whenLabel
                else stringResource(R.string.meta_pair, state.whenLabel, state.accountLabel),
                style = HalalaType.Label,
                color = HalalaColors.TextMuted,
                textAlign = TextAlign.Center
            )
        }

        HalalaCard(
            contentPadding = PaddingValues(horizontal = Spacing.card, vertical = Spacing.xs),
            verticalArrangement = Arrangement.Top
        ) {
            if (state.canCategorise) {
                DetailRow(stringResource(R.string.category), divider = false) {
                    HalalaChip(
                        label = state.category?.name ?: stringResource(R.string.choose),
                        style = if (state.category != null) ChipStyle.Plain else ChipStyle.Outline,
                        onClick = viewModel::onCategoryClick
                    )
                }
                DetailRow(stringResource(R.string.expense_type)) {
                    HalalaChip(
                        label = state.expenseType?.let { expenseTypeLabel(it) } ?: stringResource(R.string.choose),
                        style = if (state.expenseType != null) ChipStyle.Plain else ChipStyle.Outline,
                        onClick = viewModel::onTypeClick
                    )
                }
            }
            DetailRow(stringResource(R.string.transaction_kind), divider = state.canCategorise) {
                HalalaChip(kindLabel(state.kind))
            }
            state.merchantName?.let { merchant ->
                DetailRow(stringResource(R.string.merchant)) {
                    HalalaChip(label = merchant, onClick = viewModel::onMerchantClick)
                }
            }
            if (state.isMove) {
                DetailRow(stringResource(R.string.transaction_from)) { Value(state.fromLabel.orEmpty()) }
                DetailRow(stringResource(R.string.transaction_to)) { Value(state.toLabel.orEmpty()) }
            }
            if (state.note.isNotBlank()) {
                DetailRow(stringResource(R.string.transaction_note)) { Value(state.note) }
            }
        }

        state.filedBy?.let { rule ->
            HalalaCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_bolt),
                        contentDescription = null,
                        tint = HalalaColors.Accent,
                        modifier = Modifier.size(Sizes.iconSmall)
                    )
                    Text(
                        text = stringResource(R.string.filed_automatically),
                        style = HalalaType.BodyStrong,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .heightIn(min = Sizes.touchTarget)
                            .clip(Radius.sm)
                            .clickable(role = Role.Button, onClick = viewModel::onEditRuleClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.edit_rule),
                            style = HalalaType.Label,
                            color = HalalaColors.Accent
                        )
                    }
                }
                Text(
                    text = stringResource(
                        R.string.meta_pair,
                        ruleSentence(rule.words, rule.category, rule.expenseType),
                        pluralStringResource(R.plurals.rule_used, rule.hits, rule.hits)
                    ),
                    style = HalalaType.Label,
                    color = HalalaColors.TextMuted
                )
            }
        }

        // Every record says how it got here, so a reconcile's correction is never a mystery.
        HalalaCard(label = stringResource(R.string.transaction_origin)) {
            val origin = when (state.source) {
                TransactionSource.MANUAL -> stringResource(R.string.transaction_origin_manual, state.createdLabel)
                TransactionSource.RECONCILE -> stringResource(R.string.transaction_origin_reconcile, state.createdLabel)
                TransactionSource.SMS -> stringResource(R.string.transaction_origin_sms, state.createdLabel)
            }
            // Shown under its merchant's name, it says how the bank wrote it.
            val written = state.merchant.takeIf { state.merchantName != null && it != state.merchantName }
            Text(
                text = written?.let { "$origin ${stringResource(R.string.transaction_origin_descriptor, it)}" } ?: origin,
                style = HalalaType.Label,
                color = HalalaColors.TextMuted
            )
        }

        HalalaButton(
            text = stringResource(R.string.delete),
            onClick = viewModel::onDeleteClick,
            destructive = true,
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (state.isConfirmingDelete) {
        ConfirmSheet(
            title = stringResource(R.string.transaction_delete_title),
            body = stringResource(if (state.isMove) R.string.transaction_delete_move else R.string.transaction_delete_body),
            confirmLabel = stringResource(R.string.delete),
            dismissLabel = stringResource(R.string.keep),
            onConfirm = viewModel::onDeleteConfirm,
            onDismiss = viewModel::onDeleteDismiss
        )
    }

    when (val sheet = state.sheet) {
        TransactionSheet.Category -> ChoiceSheet(
            title = stringResource(R.string.category),
            options = state.categories,
            selected = state.category,
            label = { it.name },
            onPick = viewModel::onCategoryPick,
            onDismiss = viewModel::onSheetDismiss
        )

        TransactionSheet.Type -> ChoiceSheet(
            title = stringResource(R.string.expense_type),
            options = ExpenseType.entries,
            selected = state.expenseType,
            label = { expenseTypeLabel(it) },
            onPick = viewModel::onTypePick,
            onDismiss = viewModel::onSheetDismiss
        )

        is TransactionSheet.Always -> ConfirmSheet(
            title = stringResource(R.string.always_title, state.merchantName ?: state.merchant, sheet.category.name),
            body = if (sheet.others == 0) stringResource(R.string.always_body_none)
            else pluralStringResource(R.plurals.always_body, sheet.others, sheet.others),
            confirmLabel = stringResource(R.string.always_confirm),
            dismissLabel = stringResource(R.string.always_dismiss),
            onConfirm = { viewModel.onAlways(sheet.category) },
            onDismiss = viewModel::onSheetDismiss,
            destructive = false,
            onDismissClick = { viewModel.onJustThisOne(sheet.category) }
        )

        null -> Unit
    }
}

/** A label on the left and its value on the right, divided from the row above. */
@Composable
private fun DetailRow(label: String, divider: Boolean = true, value: @Composable () -> Unit) {
    Column {
        if (divider) HorizontalDivider(thickness = Sizes.border, color = HalalaColors.Line)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = DETAIL_ROW),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CardLabel(label, modifier = Modifier.weight(1f))
            value()
        }
    }
}

@Composable
private fun Value(text: String) {
    Text(text = text, style = HalalaType.Body, textAlign = TextAlign.End)
}

/** The board's 40px detail rows. */
private val DETAIL_ROW = Sizes.touchTarget - Spacing.xs
