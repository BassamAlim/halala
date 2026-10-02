package bassamalim.halala.features.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import bassamalim.halala.core.ui.components.SearchField
import bassamalim.halala.core.ui.components.TopBar
import bassamalim.halala.core.ui.components.TransactionRow
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Spacing

/**
 * Everyone you send money to or get it from, the latest first, each with where you stand: what
 * came back less what went. From the People board's "All transfers" view; its loans view, and
 * the owed-to-you cards over it, come with loans.
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

        if (!state.hasAny) {
            Text(
                text = stringResource(R.string.people_empty),
                style = HalalaType.Body,
                color = HalalaColors.TextMuted
            )
            return@Column
        }

        SearchField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            placeholder = stringResource(R.string.people_search),
            modifier = Modifier.fillMaxWidth()
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = Spacing.section)
        ) {
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
    }
}
