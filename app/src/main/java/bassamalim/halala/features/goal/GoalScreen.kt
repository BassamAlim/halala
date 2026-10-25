package bassamalim.halala.features.goal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.halala.R
import bassamalim.halala.core.ui.components.GroupLabel
import bassamalim.halala.core.ui.components.Skeleton
import bassamalim.halala.core.ui.components.TopBar
import bassamalim.halala.core.ui.components.TransactionItemRow
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Spacing
import bassamalim.halala.features.plan.GoalCardView

/**
 * One savings goal (no board: Plan's goal card, then its transactions as Category spending lists
 * them): what went into or out of it, each opening its transaction. Edit opens its form.
 */
@Composable
fun GoalScreen(viewModel: GoalViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Deleted from its form: nothing is left to show.
    LaunchedEffect(state.isGone) { if (state.isGone) viewModel.onBackClick() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screen)
            .padding(top = Insets.screenTop)
    ) {
        TopBar(
            title = state.name,
            onBack = viewModel::onBackClick,
            actionLabel = stringResource(R.string.edit).takeIf { !state.isLoading },
            onAction = viewModel::onEditClick
        )

        val card = state.card
        if (state.isLoading || card == null) {
            Skeleton()
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = Spacing.xs, bottom = Spacing.section)
        ) {
            item {
                GoalCardView(
                    goal = card,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.card)
                )
            }

            if (state.transactions.isEmpty()) item {
                Text(text = stringResource(R.string.goal_nothing_yet), style = HalalaType.Body, color = HalalaColors.TextMuted)
            }
            else item { GroupLabel(stringResource(R.string.goal_movements)) }

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
}
