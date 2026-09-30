package bassamalim.halala.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Spacing

/**
 * A tab whose screens haven't been built yet: its title and one honest card saying what will
 * live here. [content] goes under the card, for what already works.
 */
@Composable
fun PlaceholderTab(
    title: String,
    body: String,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen)
            .padding(top = Insets.screenTop, bottom = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.card)
    ) {
        ScreenTitle(title)
        content()
        HalalaCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = body, style = HalalaType.Body, color = HalalaColors.TextMuted)
        }
    }
}
