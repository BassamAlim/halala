package bassamalim.halala.features.person

import bassamalim.halala.core.ui.components.Skeleton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.halala.R
import bassamalim.halala.core.enums.AmountTone
import bassamalim.halala.core.ui.components.Avatar
import bassamalim.halala.core.ui.components.ButtonKind
import bassamalim.halala.core.ui.components.ConfirmSheet
import bassamalim.halala.core.ui.components.DateDialog
import bassamalim.halala.core.ui.components.FormField
import bassamalim.halala.core.ui.components.GroupLabel
import bassamalim.halala.core.ui.components.HalalaButton
import bassamalim.halala.core.ui.components.HalalaCard
import bassamalim.halala.core.ui.components.HalalaSheet
import bassamalim.halala.core.ui.components.HalalaTextField
import bassamalim.halala.core.ui.components.ListCard
import bassamalim.halala.core.ui.components.ListRow
import bassamalim.halala.core.ui.components.SearchField
import bassamalim.halala.core.ui.components.TopBar
import bassamalim.halala.core.ui.components.TransactionItemRow
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaNumbers
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Spacing

/**
 * One person, from the Person board: each open loan (what is still owed, the reminder, recording
 * a repayment, and what happened to it), settled ones, then all transfers with them (sent,
 * received, net) and their feed. A spelling
 * that joined them by mistake is taken out from here, and someone who is another by a
 * different name is merged from here, as on the Merchant screen.
 */
@Composable
fun PersonScreen(viewModel: PersonViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screen)
            .padding(top = Insets.screenTop)
    ) {
        TopBar(
            title = stringResource(R.string.person),
            onBack = viewModel::onBackClick,
            actionLabel = stringResource(R.string.merchant_rename),
            actionEnabled = !state.isLoading,
            onAction = viewModel::onRenameClick
        )

        if (state.isLoading) {
            Skeleton()
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = Spacing.xs, bottom = Spacing.section)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.card),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Avatar(initial = state.initial)
                    Text(text = state.name, style = HalalaType.Title, textAlign = TextAlign.Center)
                }
            }

            val open = state.loans.filter { it.open }
            val settled = state.loans.filterNot { it.open }
            items(open, key = { "loan-${it.loanId}" }) { loan ->
                Column(Modifier.padding(bottom = Spacing.card)) { OpenLoan(loan, state.name, viewModel) }
            }
            if (open.isNotEmpty()) item {
                Text(
                    text = stringResource(R.string.loan_hint, state.name),
                    style = HalalaType.Caption,
                    color = HalalaColors.TextMuted,
                    modifier = Modifier.padding(bottom = Spacing.card)
                )
            }
            items(settled, key = { "loan-${it.loanId}" }) { loan ->
                Column(Modifier.padding(bottom = Spacing.card)) { SettledLoan(loan, viewModel) }
            }

            item {
                HalalaCard(
                    label = stringResource(R.string.person_transfers, state.name),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.card)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Figure(stringResource(R.string.person_sent), state.sent, HalalaColors.Text, Modifier.weight(1f))
                        Figure(stringResource(R.string.person_received), state.received, HalalaColors.Income, Modifier.weight(1f))
                        Figure(
                            label = stringResource(R.string.person_net),
                            value = state.net,
                            color = if (state.netTone == AmountTone.Income) HalalaColors.Income else HalalaColors.Text,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        text = pluralStringResource(R.plurals.transfer_count, state.count, state.count),
                        style = HalalaType.Label,
                        color = HalalaColors.TextMuted
                    )
                }
            }

            state.salarySince?.let { since ->
                item {
                    Column(
                        modifier = Modifier.padding(bottom = Spacing.card),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        GroupLabel(stringResource(R.string.salary))
                        ListCard(Modifier.fillMaxWidth()) {
                            ListRow(
                                title = stringResource(R.string.salary_pays_you),
                                subtitle = stringResource(R.string.salary_since, since)
                            )
                        }
                        HalalaButton(
                            text = stringResource(R.string.salary_stop),
                            onClick = viewModel::onSalaryStopClick,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier.padding(bottom = Spacing.card),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    GroupLabel(stringResource(R.string.person_spellings))
                    ListCard(Modifier.fillMaxWidth()) {
                        state.spellings.forEachIndexed { index, spelling ->
                            ListRow(
                                title = spelling.descriptor,
                                subtitle = pluralStringResource(R.plurals.transfer_count, spelling.count, spelling.count),
                                divider = index > 0,
                                onClick = if (state.canSplit) ({ viewModel.onSpellingClick(spelling) }) else null
                            )
                        }
                    }
                }
            }

            item {
                HalalaButton(
                    text = stringResource(R.string.person_merge),
                    onClick = viewModel::onMergeClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.card)
                )
            }

            if (state.transactions.isNotEmpty()) item {
                GroupLabel(stringResource(R.string.merchant_transactions))
            }

            itemsIndexed(state.transactions, key = { _, item -> item.id }) { index, item ->
                TransactionItemRow(
                    item = item,
                    onClick = { viewModel.onTransactionClick(item.id) },
                    divider = index > 0,
                    withDay = true
                )
            }
        }
    }

    when (val sheet = state.sheet) {
        is PersonSheet.Rename -> HalalaSheet(onDismiss = viewModel::onSheetDismiss) {
            Text(text = stringResource(R.string.merchant_rename), style = HalalaType.Title)
            FormField(
                label = stringResource(R.string.merchant_name),
                error = if (sheet.problem == NameProblem.Missing) stringResource(R.string.merchant_name_missing) else null
            ) {
                HalalaTextField(
                    value = sheet.name,
                    onValueChange = viewModel::onNameChange,
                    isError = sheet.problem != null,
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done
                )
            }
            HalalaButton(
                text = stringResource(R.string.save),
                onClick = viewModel::onRenameSave,
                kind = ButtonKind.Primary,
                modifier = Modifier.fillMaxWidth()
            )
        }

        is PersonSheet.Split -> ConfirmSheet(
            title = stringResource(R.string.merchant_split_title, sheet.spelling.descriptor, state.name),
            body = stringResource(R.string.person_split_body),
            confirmLabel = stringResource(R.string.merchant_split_confirm),
            dismissLabel = stringResource(R.string.cancel),
            onConfirm = viewModel::onSplitConfirm,
            onDismiss = viewModel::onSheetDismiss,
            destructive = false
        )

        is PersonSheet.Merge -> HalalaSheet(onDismiss = viewModel::onSheetDismiss) {
            Text(text = stringResource(R.string.person_merge_title, state.name), style = HalalaType.Title)
            SearchField(
                value = sheet.query,
                onValueChange = viewModel::onMergeQueryChange,
                placeholder = stringResource(R.string.people_search),
                modifier = Modifier.fillMaxWidth()
            )
            if (state.mergeOptions.isEmpty()) {
                Text(
                    text = stringResource(R.string.person_merge_none),
                    style = HalalaType.Label,
                    color = HalalaColors.TextMuted
                )
            } else ListCard(Modifier.fillMaxWidth()) {
                state.mergeOptions.forEachIndexed { index, option ->
                    ListRow(
                        title = option.name,
                        subtitle = pluralStringResource(R.plurals.transfer_count, option.count, option.count),
                        divider = index > 0,
                        onClick = { viewModel.onMergePick(option) }
                    )
                }
            }
        }

        is PersonSheet.ConfirmMerge -> ConfirmSheet(
            title = stringResource(R.string.merchant_merge_confirm_title, state.name, sheet.into.name),
            body = stringResource(R.string.person_merge_confirm_body, state.name, sheet.into.name),
            confirmLabel = stringResource(R.string.merchant_merge_confirm),
            dismissLabel = stringResource(R.string.cancel),
            onConfirm = viewModel::onMergeConfirm,
            onDismiss = viewModel::onSheetDismiss,
            destructive = false
        )

        is PersonSheet.Repay -> HalalaSheet(onDismiss = viewModel::onSheetDismiss) {
            val loan = state.loans.firstOrNull { it.loanId == sheet.loanId }
            Text(text = stringResource(R.string.loan_repay_title), style = HalalaType.Title)
            if (loan == null || loan.candidates.isEmpty()) Text(
                text = stringResource(
                    if (loan?.lent != false) R.string.loan_repay_none_lent else R.string.loan_repay_none_borrowed,
                    state.name
                ),
                style = HalalaType.Label,
                color = HalalaColors.TextMuted
            ) else Column {
                loan.candidates.forEachIndexed { index, item ->
                    TransactionItemRow(
                        item = item,
                        onClick = { viewModel.onRepayPick(item.id) },
                        divider = index > 0,
                        withDay = true
                    )
                }
            }
            HalalaButton(
                text = stringResource(R.string.loan_forgive),
                onClick = viewModel::onForgiveClick,
                destructive = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        is PersonSheet.Forgive -> ConfirmSheet(
            title = stringResource(R.string.loan_forgive_title),
            body = stringResource(R.string.loan_forgive_body),
            confirmLabel = stringResource(R.string.loan_forgive_confirm),
            dismissLabel = stringResource(R.string.cancel),
            onConfirm = viewModel::onForgiveConfirm,
            onDismiss = viewModel::onSheetDismiss
        )

        is PersonSheet.Due -> DateDialog(
            date = sheet.date,
            onPicked = viewModel::onDuePicked,
            onDismiss = viewModel::onSheetDismiss
        )

        null -> Unit
    }
}

/** One of the transfers card's three figures: a label over a number. */
@Composable
private fun Figure(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(text = label, style = HalalaType.Caption, color = HalalaColors.TextMuted)
        Text(text = value, style = HalalaNumbers.Amount, color = color)
    }
}
