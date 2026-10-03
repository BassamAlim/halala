package bassamalim.halala.features.transaction

import bassamalim.halala.core.ui.components.Skeleton
import bassamalim.halala.core.ui.components.HeatMap
import bassamalim.halala.core.ui.components.HeatPoint
import bassamalim.halala.core.ui.components.MapPlaceholder
import bassamalim.halala.core.ui.components.openLocationSettings
import bassamalim.halala.core.ui.components.rememberLocationRequest
import bassamalim.halala.core.places.LocationAccess
import androidx.compose.foundation.layout.height
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
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
import androidx.compose.foundation.layout.ColumnScope
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
 * its category and type, its tags, where a purchase was made, its loan and split, the rule that
 * filed it, and how it got here. The raw SMS isn't shown yet.
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
            Avatar(initial = state.initial, tone = state.tone, merchantId = state.merchantId.takeIf { state.personName == null })
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

        if (state.showsPlace) PlaceCard(state, viewModel)

        state.loan?.let { loan -> LoanCard(loan, viewModel) }

        state.goal?.let { goal ->
            HalalaCard(label = stringResource(R.string.goal_toward_label)) {
                Text(
                    text = stringResource(if (goal.withdrawn) R.string.goal_taken_out_of else R.string.goal_toward, goal.name),
                    style = HalalaType.Body
                )
                HalalaButton(
                    text = stringResource(R.string.goal_toward_undo),
                    onClick = viewModel::onUngoalClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xs)
                )
            }
        }
        if (state.canMarkSalary) HalalaButton(
            text = stringResource(R.string.salary_mark),
            onClick = viewModel::onSalaryClick,
            modifier = Modifier.fillMaxWidth()
        )

        if (state.canMarkGoal) HalalaButton(
            text = stringResource(R.string.goal_toward_mark),
            onClick = viewModel::onGoalClick,
            modifier = Modifier.fillMaxWidth()
        )

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
            state.foreign?.let { foreign ->
                Text(
                    text = stringResource(
                        if (foreign.estimated) R.string.transaction_foreign_estimated else R.string.transaction_foreign,
                        foreign.amount, foreign.currency, foreign.rate, state.currency
                    ),
                    style = HalalaType.Label,
                    color = HalalaColors.TextMuted
                )
            }
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
            onDismiss = viewModel::onSheetDismiss,
            addLabel = stringResource(R.string.category_new_chip),
            onAdd = viewModel::onNewCategoryClick
        )

        TransactionSheet.Repay -> (state.loan as? LoanLink.Open)?.let { loan ->
            ChoiceSheet(
                title = stringResource(R.string.loan_repays_title),
                options = loan.suggestions,
                selected = null,
                label = {
                    stringResource(
                        R.string.loan_repay_option, it.remaining, it.currency,
                        stringResource(if (it.lent) R.string.loan_lent_on else R.string.loan_borrowed_on, it.lentOn)
                    )
                },
                onPick = viewModel::onRepayPick,
                onDismiss = viewModel::onSheetDismiss
            )
        }

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

        is TransactionSheet.Goal -> HalalaSheet(onDismiss = viewModel::onSheetDismiss) {
            Text(text = stringResource(R.string.goal_toward_mark), style = HalalaType.Title)
            SegmentedControl(
                options = listOf(stringResource(R.string.goal_put_in), stringResource(R.string.goal_took_out)),
                selectedIndex = if (sheet.withdrawn) 1 else 0,
                onSelect = { viewModel.onGoalWayClick(it == 1) }
            )
            ChoiceChipsMulti(options = state.goals, selected = listOfNotNull(sheet.goalId), onToggle = viewModel::onGoalPick)
            if (sheet.noGoal) Text(text = stringResource(R.string.goal_toward_none), style = HalalaType.Label, color = HalalaColors.StateOver)
            Text(text = stringResource(R.string.goal_toward_body), style = HalalaType.Label, color = HalalaColors.TextMuted)
            HalalaButton(
                text = stringResource(R.string.goal_toward_confirm),
                onClick = viewModel::onGoalConfirm,
                kind = ButtonKind.Primary,
                modifier = Modifier.fillMaxWidth()
            )
        }

        TransactionSheet.Ungoal -> ConfirmSheet(
            title = stringResource(R.string.goal_toward_undo_title),
            body = stringResource(R.string.goal_toward_undo_body),
            confirmLabel = stringResource(R.string.goal_toward_undo),
            dismissLabel = stringResource(R.string.cancel),
            onConfirm = viewModel::onUngoalConfirm,
            onDismiss = viewModel::onSheetDismiss,
            destructive = false
        )

        TransactionSheet.Salary -> ConfirmSheet(
            title = stringResource(R.string.salary_mark_title, state.personName.orEmpty()),
            body = stringResource(R.string.salary_mark_body, state.personName.orEmpty()),
            confirmLabel = stringResource(R.string.salary_mark),
            dismissLabel = stringResource(R.string.cancel),
            onConfirm = viewModel::onSalaryConfirm,
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
            val several = loan.suggestions.size > 1
            if (several) Text(
                text = pluralStringResource(
                    if (loan.suggestions.first().lent) R.plurals.loan_suggest_many_lent else R.plurals.loan_suggest_many_borrowed,
                    loan.suggestions.size, loan.person, loan.suggestions.size
                ),
                style = HalalaType.Body
            )
            else loan.suggestions.firstOrNull()?.let { suggestion ->
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
            }
            if (loan.suggestions.isNotEmpty()) {
                HalalaButton(
                    text = stringResource(if (several) R.string.loan_repays_which else R.string.loan_repays),
                    onClick = viewModel::onRepaysClick,
                    kind = ButtonKind.Primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xs)
                )
            }
            HalalaButton(
                text = stringResource(if (loan.lent) R.string.loan_mark_lent else R.string.loan_mark_borrowed),
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
/**
 * Where a purchase was made: a small map of the spot, or the drawn street grid saying why there
 * is none (location not allowed all the time, location off, or nothing kept for this one, as for
 * history from before). The map is a picture here: the page scrolls over it.
 */
@Composable
private fun PlaceCard(state: TransactionUiState, viewModel: TransactionViewModel) {
    val context = LocalContext.current
    val askLocation = rememberLocationRequest(viewModel::onCheckLocation)
    LifecycleResumeEffect(viewModel) {
        viewModel.onCheckLocation()
        onPauseOrDispose { }
    }
    val mapModifier = Modifier
        .fillMaxWidth()
        .height(Sizes.placeMap)
        .clip(Radius.lg)

    HalalaCard(label = stringResource(R.string.transaction_place)) {
        val place = state.place
        when {
            place != null -> {
                Box(mapModifier) {
                    HeatMap(
                        points = listOf(HeatPoint(place.latitude, place.longitude, 1f)),
                        focus = null,
                        modifier = Modifier.matchParentSize()
                    )
                    // Takes the touches so the map stays put and the page scrolls.
                    Box(Modifier.matchParentSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } })
                }
                Text(
                    text = stringResource(R.string.transaction_place_within, place.accuracyMeters),
                    style = HalalaType.Label,
                    color = HalalaColors.TextMuted
                )
            }
            state.locationAccess == LocationAccess.SERVICES_OFF -> MapPlaceholder(
                message = stringResource(R.string.map_location_off),
                action = stringResource(R.string.map_turn_location_on),
                onAction = { openLocationSettings(context) },
                modifier = mapModifier
            )
            state.locationAccess == LocationAccess.FOREGROUND_ONLY -> MapPlaceholder(
                message = stringResource(R.string.map_needs_always),
                action = stringResource(R.string.map_allow_always),
                onAction = askLocation,
                modifier = mapModifier
            )
            state.locationAccess == LocationAccess.DENIED -> MapPlaceholder(
                message = stringResource(R.string.map_no_permission),
                action = stringResource(R.string.map_allow),
                onAction = askLocation,
                modifier = mapModifier
            )
            else -> MapPlaceholder(
                message = stringResource(R.string.transaction_place_none),
                action = null,
                onAction = {},
                modifier = mapModifier
            )
        }
    }
}

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
private fun ColumnScope.ChoiceChipsMulti(options: List<PersonChoice>, selected: List<Long>, onToggle: (Long) -> Unit) {
    // Everyone you transfer with can be many: they scroll, so what comes after stays on screen.
    FlowRow(
        modifier = Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
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
