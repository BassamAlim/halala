package bassamalim.halala.features.transaction

import bassamalim.halala.core.ui.components.Skeleton
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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
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
import bassamalim.halala.features.tags.TransactionTags
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
import bassamalim.halala.core.ui.components.ButtonKind
import bassamalim.halala.core.ui.components.DateDialog
import bassamalim.halala.core.ui.components.HalalaSheet
import bassamalim.halala.core.ui.components.ListCard
import bassamalim.halala.core.ui.components.ListRow
import bassamalim.halala.core.ui.components.MONEY_MARK
import bassamalim.halala.core.ui.components.MoneyText
import bassamalim.halala.core.ui.components.HalalaTextField
import bassamalim.halala.core.ui.components.SegmentedControl
import bassamalim.halala.core.domain.SplitProblem
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import bassamalim.halala.core.ui.components.rememberNotificationAsk
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
import bassamalim.halala.core.ui.businessTypeLabel
import bassamalim.halala.core.ui.identifiedLabel

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

        if (state.isLoading) {
            Skeleton()
            return@Column
        }

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
            state.personName?.let { person ->
                DetailRow(stringResource(R.string.person)) {
                    HalalaChip(label = person, onClick = viewModel::onPersonClick)
                }
            }
            state.merchantName?.takeIf { state.personName == null }?.let { merchant ->
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
            val tagsLabel = stringResource(R.string.tags)
            TransactionTags(row = { value -> DetailRow(tagsLabel) { value() } })
        }

        state.loan?.let { loan -> LoanCard(loan, viewModel) }

        state.split?.let { split ->
            HalalaCard(label = stringResource(if (split.whole) R.string.paid_for else R.string.split)) {
                split.shares.forEach { (name, share) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = stringResource(R.string.split_owes, name), style = HalalaType.Body)
                        Text(text = share, style = HalalaNumbers.Amount)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = stringResource(R.string.split_yours), style = HalalaType.Label, color = HalalaColors.TextMuted)
                    Text(text = split.yours, style = HalalaNumbers.Amount, color = HalalaColors.TextMuted)
                }
                HalalaButton(
                    text = stringResource(if (split.whole) R.string.paid_for_undo else R.string.split_undo),
                    onClick = viewModel::onUnsplitClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xs)
                )
            }
        }
        if (state.canSplit) HalalaButton(
            text = stringResource(R.string.split_with),
            onClick = viewModel::onSplitClick,
            modifier = Modifier.fillMaxWidth()
        )
        if (state.canPayFor) HalalaButton(
            text = stringResource(R.string.paid_for_mark),
            onClick = viewModel::onPayForClick,
            modifier = Modifier.fillMaxWidth()
        )

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
                // An automatic rule says why: what the merchant is, and who said so.
                rule.identifiedAs?.let { type ->
                    val identified = stringResource(R.string.identified_as, businessTypeLabel(type))
                    Text(
                        text = rule.identifiedBy
                            ?.let { stringResource(R.string.meta_pair, identified, identifiedLabel(it, rule.confidence)) }
                            ?: identified,
                        style = HalalaType.Label,
                        color = HalalaColors.TextMuted
                    )
                }
            }
        }

        // Every record says how it got here, so a reconcile's correction is never a mystery.
        HalalaCard(label = stringResource(R.string.transaction_origin)) {
            val origin = when (state.source) {
                TransactionSource.MANUAL -> stringResource(R.string.transaction_origin_manual, state.createdLabel)
                TransactionSource.RECONCILE -> stringResource(R.string.transaction_origin_reconcile, state.createdLabel)
                TransactionSource.SMS -> stringResource(R.string.transaction_origin_sms, state.createdLabel)
            }
            // Shown under its merchant's or person's name, it says how the bank wrote it.
            val shownAs = state.personName ?: state.merchantName
            val written = state.merchant.takeIf { shownAs != null && it != shownAs }
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

        is TransactionSheet.MarkLoan -> {
            val open = state.loan as? LoanLink.Open
            val askToNotify = rememberNotificationAsk()
            val chosen = state.people.firstOrNull { it.id == sheet.personId }?.name
            HalalaSheet(onDismiss = viewModel::onSheetDismiss) {
                Text(
                    text = when {
                        chosen == null -> stringResource(if (sheet.forPurchase) R.string.paid_for_title_none else R.string.loan_mark_title_none)
                        sheet.forPurchase -> stringResource(R.string.paid_for_title, chosen)
                        else -> stringResource(
                            if (open?.lent != false) R.string.loan_mark_lent_title else R.string.loan_mark_borrowed_title,
                            chosen
                        )
                    },
                    style = HalalaType.Title
                )
                Text(
                    text = stringResource(if (sheet.forPurchase) R.string.paid_for_body else R.string.loan_mark_body),
                    style = HalalaType.Label,
                    color = HalalaColors.TextMuted
                )
                if (state.people.isNotEmpty()) ChoiceChipsMulti(
                    options = state.people,
                    selected = listOfNotNull(sheet.personId),
                    onToggle = viewModel::onLoanPersonClick
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    HalalaTextField(
                        value = sheet.newName,
                        onValueChange = viewModel::onLoanNameChange,
                        placeholder = stringResource(R.string.split_someone_else),
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                        modifier = Modifier.weight(1f)
                    )
                    HalalaButton(
                        text = stringResource(R.string.recurring_add),
                        onClick = viewModel::onLoanAddPerson,
                        enabled = sheet.newName.isNotBlank()
                    )
                }
                if (sheet.noOne) Text(
                    text = stringResource(if (sheet.forPurchase) R.string.paid_for_no_one else R.string.loan_no_one),
                    style = HalalaType.Label,
                    color = HalalaColors.StateOver
                )
                ListCard(Modifier.fillMaxWidth()) {
                    ListRow(
                        title = stringResource(R.string.loan_due),
                        subtitle = sheet.dueLabel ?: stringResource(R.string.loan_due_none),
                        onClick = {
                            askToNotify()
                            viewModel.onDueClick()
                        }
                    )
                }
                HalalaButton(
                    text = stringResource(R.string.loan_mark_confirm),
                    onClick = viewModel::onMarkLoanConfirm,
                    kind = ButtonKind.Primary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (sheet.picking) DateDialog(
                date = sheet.dueOn ?: sheet.pickFrom,
                onPicked = viewModel::onDuePicked,
                onDismiss = viewModel::onDuePickDismiss
            )
        }

        is TransactionSheet.Split -> HalalaSheet(onDismiss = viewModel::onSheetDismiss) {
            Text(text = stringResource(R.string.split_title), style = HalalaType.Title)
            SegmentedControl(
                options = listOf(stringResource(R.string.split_equally), stringResource(R.string.split_by_amount)),
                selectedIndex = if (sheet.byAmount) 1 else 0,
                onSelect = { viewModel.onSplitModeClick(it == 1) }
            )
            if (state.people.isNotEmpty()) ChoiceChipsMulti(
                options = state.people,
                selected = sheet.selected,
                onToggle = viewModel::onSplitPersonClick
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                HalalaTextField(
                    value = sheet.newName,
                    onValueChange = viewModel::onSplitNameChange,
                    placeholder = stringResource(R.string.split_someone_else),
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                    modifier = Modifier.weight(1f)
                )
                HalalaButton(
                    text = stringResource(R.string.recurring_add),
                    onClick = viewModel::onSplitAddPerson,
                    enabled = sheet.newName.isNotBlank()
                )
            }
            ListCard(Modifier.fillMaxWidth()) {
                sheet.selected.forEachIndexed { index, personId ->
                    val name = state.people.firstOrNull { it.id == personId }?.name.orEmpty()
                    ListRow(
                        title = name,
                        divider = index > 0,
                        trailing = {
                            if (sheet.byAmount) HalalaTextField(
                                value = sheet.amounts[personId].orEmpty(),
                                onValueChange = { viewModel.onSplitAmountChange(personId, it) },
                                numeric = true,
                                isError = SplitProblem.ShareMissing in sheet.problems && sheet.preview[personId] == null,
                                modifier = Modifier.width(SHARE_FIELD)
                            ) else Text(
                                text = sheet.preview[personId].orEmpty(),
                                style = HalalaNumbers.Amount,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                        }
                    )
                }
                ListRow(
                    title = stringResource(R.string.split_yours),
                    divider = sheet.selected.isNotEmpty(),
                    trailing = {
                        Text(
                            text = sheet.yours,
                            style = HalalaNumbers.Amount,
                            color = HalalaColors.TextMuted,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                )
            }
            val problem = when {
                SplitProblem.NoOne in sheet.problems -> R.string.split_no_one
                SplitProblem.TooMuch in sheet.problems -> R.string.split_too_much
                SplitProblem.ShareMissing in sheet.problems -> R.string.amount_invalid
                else -> null
            }
            problem?.let { Text(text = stringResource(it), style = HalalaType.Label, color = HalalaColors.StateOver) }
            Text(text = stringResource(R.string.split_body), style = HalalaType.Label, color = HalalaColors.TextMuted)
            HalalaButton(
                text = stringResource(R.string.split_confirm),
                onClick = viewModel::onSplitConfirm,
                kind = ButtonKind.Primary,
                modifier = Modifier.fillMaxWidth()
            )
        }

        is TransactionSheet.Unsplit -> ConfirmSheet(
            title = stringResource(if (sheet.whole) R.string.paid_for_undo_title else R.string.split_undo_title),
            body = stringResource(if (sheet.whole) R.string.paid_for_undo_body else R.string.split_undo_body),
            confirmLabel = stringResource(if (sheet.whole) R.string.paid_for_undo else R.string.split_undo),
            dismissLabel = stringResource(R.string.cancel),
            onConfirm = viewModel::onUnsplitConfirm,
            onDismiss = viewModel::onSheetDismiss,
            destructive = false
        )

        TransactionSheet.Unlink -> ConfirmSheet(
            title = stringResource(R.string.loan_unlink_title),
            body = stringResource(R.string.loan_unlink_body),
            confirmLabel = stringResource(R.string.loan_unlink_confirm),
            dismissLabel = stringResource(R.string.cancel),
            onConfirm = viewModel::onUnlinkConfirm,
            onDismiss = viewModel::onSheetDismiss,
            destructive = false
        )

        null -> Unit
    }
}

/**
 * A transfer to or from someone and loans: one it would repay (asked, one tap to say yes),
 * marking it as lending or borrowing, or the loan it is part of, which opens the person.
 */
@Composable
private fun LoanCard(loan: LoanLink, viewModel: TransactionViewModel) {
    when (loan) {
        is LoanLink.Open -> HalalaCard(label = stringResource(R.string.loan)) {
            loan.suggestion?.let { suggestion ->
                MoneyText(
                    text = stringResource(
                        if (suggestion.lent) R.string.loan_suggest_lent else R.string.loan_suggest_borrowed,
                        loan.person, MONEY_MARK, suggestion.lentOn
                    ),
                    amount = suggestion.remaining,
                    currency = suggestion.currency,
                    style = HalalaType.Body,
                    color = HalalaColors.Text
                )
                HalalaButton(
                    text = stringResource(R.string.loan_repays),
                    onClick = viewModel::onRepaysClick,
                    kind = ButtonKind.Primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xs)
                )
            }
            HalalaButton(
                text = if (loan.person.isBlank()) stringResource(R.string.loan_mark_confirm)
                else stringResource(if (loan.lent) R.string.loan_mark_lent else R.string.loan_mark_borrowed, loan.person),
                onClick = viewModel::onMarkLoanClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xs)
            )
        }

        is LoanLink.Part -> HalalaCard(label = stringResource(R.string.loan), onClick = viewModel::onLoanClick) {
            val what = stringResource(
                when {
                    loan.repays && loan.lent -> R.string.loan_part_repaid_to_you
                    loan.repays -> R.string.loan_part_you_repaid
                    loan.lent -> R.string.loan_part_lent
                    else -> R.string.loan_part_borrowed
                },
                loan.person
            )
            Text(text = what, style = HalalaType.Body)
            if (loan.settled) Text(text = stringResource(R.string.loan_settled), style = HalalaType.Label, color = HalalaColors.TextMuted)
            else MoneyText(
                text = stringResource(R.string.loan_still_owed, MONEY_MARK),
                amount = loan.remaining,
                currency = loan.currency,
                style = HalalaType.Label,
                color = HalalaColors.TextMuted
            )
            HalalaButton(
                text = stringResource(R.string.loan_unlink),
                onClick = viewModel::onUnlinkClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xs)
            )
        }
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

/** Choosing several people: each chip on while picked. */
@Composable
private fun ChoiceChipsMulti(options: List<PersonChoice>, selected: List<Long>, onToggle: (Long) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        options.forEach { option ->
            HalalaChip(
                label = option.name,
                style = if (option.id in selected) ChipStyle.On else ChipStyle.Outline,
                onClick = { onToggle(option.id) }
            )
        }
    }
}

/** A share typed beside a name. */
private val SHARE_FIELD = Sizes.fab * 2

/** The board's 40px detail rows. */
private val DETAIL_ROW = Sizes.touchTarget - Spacing.xs
