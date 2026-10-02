package bassamalim.halala.features.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.halala.R
import bassamalim.halala.core.domain.People
import bassamalim.halala.core.ui.components.GroupLabel
import bassamalim.halala.core.ui.components.HalalaButton
import bassamalim.halala.core.ui.components.HalalaCard
import bassamalim.halala.core.ui.components.SearchField
import bassamalim.halala.core.ui.components.SegmentedControl
import bassamalim.halala.core.ui.components.SummaryCard
import bassamalim.halala.core.ui.components.TopBar
import bassamalim.halala.core.ui.components.TransactionRow
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Spacing

/**
 * The People board: what is owed each way, then the loans (open, then settled) or everyone you
 * transfer with, the latest first, each with what came back less what went. Above either, with
 * no board: the pairs that may be one person, to merge or not.
 */
@Composable
fun PeopleScreen(viewModel: PeopleViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screen)
            .padding(top = Insets.screenTop),
        verticalArrangement = Arrangement.spacedBy(Spacing.card)
    ) {
        TopBar(title = stringResource(R.string.people), onBack = viewModel::onBackClick)

        if (state.isLoading) return@Column

        Row(horizontalArrangement = Arrangement.spacedBy(Insets.grid)) {
            SummaryCard(
                label = stringResource(R.string.people_owed_to_you),
                amount = state.owedToYou,
                amountColor = HalalaColors.Income,
                modifier = Modifier.weight(1f)
            )
            SummaryCard(
                label = stringResource(R.string.people_you_owe),
                amount = state.youOwe,
                modifier = Modifier.weight(1f)
            )
        }

        SegmentedControl(
            options = listOf(stringResource(R.string.people_loans), stringResource(R.string.people_all_transfers)),
            selectedIndex = state.view.ordinal,
            onSelect = viewModel::onViewChange
        )

        if (state.view == PeopleView.TRANSFERS && state.hasAny) SearchField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            placeholder = stringResource(R.string.people_search),
            modifier = Modifier.fillMaxWidth()
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = Spacing.section)
        ) {
            suggestions(state, viewModel)
            when (state.view) {
                PeopleView.LOANS -> loans(state, viewModel)
                PeopleView.TRANSFERS -> transfers(state, viewModel)
            }
        }
    }
}

/** Pairs that may be one person, above either view: you say, Halala never merges on its own. */
private fun LazyListScope.suggestions(state: PeopleUiState, viewModel: PeopleViewModel) {
    if (state.suggestions.isEmpty()) return
    item(key = "same") { GroupLabel(stringResource(R.string.people_same_title), Modifier.padding(top = Spacing.xs)) }
    items(state.suggestions, key = { "same-${it.key}" }) { row ->
        HalalaCard(
            modifier = Modifier.padding(bottom = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(text = stringResource(R.string.people_same_pair, row.goesName, row.keepName), style = HalalaType.Body)
            Text(
                text = when (row.reason) {
                    People.MergeReason.SPELLING -> stringResource(R.string.people_same_spelling, row.keepName)
                    People.MergeReason.ACCOUNT -> stringResource(R.string.people_same_account, row.keepName, row.ref.orEmpty())
                    People.MergeReason.AI -> stringResource(R.string.people_same_ai, row.keepName)
                },
                style = HalalaType.Caption,
                color = HalalaColors.TextMuted
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                HalalaButton(stringResource(R.string.people_same_merge), { viewModel.onMerge(row) }, Modifier.weight(1f))
                HalalaButton(stringResource(R.string.people_same_not), { viewModel.onNotSame(row) }, Modifier.weight(1f))
            }
        }
    }
}

private fun LazyListScope.loans(state: PeopleUiState, viewModel: PeopleViewModel) {
    if (state.openLoans.isEmpty() && state.settledLoans.isEmpty()) {
        item { Empty(stringResource(R.string.people_loans_empty)) }
        return
    }
    for ((label, rows) in listOf(R.string.people_open to state.openLoans, R.string.people_settled to state.settledLoans)) {
        if (rows.isEmpty()) continue
        item(key = label) { GroupLabel(stringResource(label), Modifier.padding(top = Spacing.xs)) }
        itemsIndexed(rows, key = { _, row -> "loan-${row.loanId}" }) { index, row ->
            TransactionRow(
                title = row.name,
                meta = loanMeta(row),
                amount = row.amount,
                tone = row.tone,
                initial = row.initial,
                divider = index > 0,
                muted = row.settledLabel != null,
                onClick = { viewModel.onPersonClick(row.personId) }
            )
        }
    }
}

private fun LazyListScope.transfers(state: PeopleUiState, viewModel: PeopleViewModel) {
    if (!state.hasAny) {
        item { Empty(stringResource(R.string.people_empty)) }
        return
    }
    itemsIndexed(state.people, key = { _, person -> person.id }) { index, person ->
        val count = pluralStringResource(R.plurals.transfer_count, person.transfers, person.transfers)
        TransactionRow(
            title = person.name,
            meta = if (person.lastDate.isEmpty()) count
            else stringResource(R.string.meta_pair, count, stringResource(R.string.people_last, person.lastDate)),
            amount = person.net,
            tone = person.tone,
            initial = person.initial,
            divider = index > 0,
            onClick = { viewModel.onPersonClick(person.id) }
        )
    }
    item {
        Text(
            text = stringResource(R.string.people_hint),
            style = HalalaType.Caption,
            color = HalalaColors.TextMuted,
            modifier = Modifier.padding(top = Spacing.card)
        )
    }
}

/** "Owes you · due 15 Oct", "You owe · lent 12 Sep", "Repaid 12 Aug", "Forgiven 3 Sep". */
@Composable
private fun loanMeta(row: LoanRow): String {
    row.settledLabel?.let { day ->
        return stringResource(
            when {
                row.forgiven -> R.string.loan_forgiven_on
                row.lent -> R.string.loan_repaid_on
                else -> R.string.loan_you_repaid_on
            },
            day
        )
    }
    val who = stringResource(if (row.lent) R.string.loan_owes_you else R.string.loan_you_owe)
    val timing = row.dueLabel?.let { stringResource(R.string.loan_due_on, it) }
        ?: row.lentOnLabel?.let { stringResource(if (row.lent) R.string.loan_lent_on else R.string.loan_borrowed_on, it) }
    return if (timing == null) who else stringResource(R.string.meta_pair, who, timing)
}

@Composable
private fun Empty(text: String) {
    Text(text = text, style = HalalaType.Body, color = HalalaColors.TextMuted, modifier = Modifier.padding(top = Spacing.xs))
}
