package bassamalim.halala.features.assistant

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import bassamalim.halala.R
import bassamalim.halala.core.ui.components.ListCard
import bassamalim.halala.core.ui.components.ListRow
import bassamalim.halala.core.ui.components.PlaceholderTab

/** The digests archive; the chat arrives in Phase 6. */
@Composable
fun AssistantScreen(viewModel: AssistantViewModel = hiltViewModel()) {
    PlaceholderTab(
        title = stringResource(R.string.tab_assistant),
        body = stringResource(R.string.assistant_placeholder)
    ) {
        ListCard(Modifier.fillMaxWidth()) {
            ListRow(
                title = stringResource(R.string.digests),
                subtitle = stringResource(R.string.digests_summary),
                onClick = viewModel::onDigestsClick
            )
        }
    }
}
