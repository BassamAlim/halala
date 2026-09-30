package bassamalim.halala.features.assistant

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import bassamalim.halala.R
import bassamalim.halala.core.ui.components.PlaceholderTab

/** The chat and the digests archive arrive in Phases 4 and 6. */
@Composable
fun AssistantScreen() {
    PlaceholderTab(
        title = stringResource(R.string.tab_assistant),
        body = stringResource(R.string.assistant_placeholder)
    )
}
