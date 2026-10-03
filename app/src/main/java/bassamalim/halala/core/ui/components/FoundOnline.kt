package bassamalim.halala.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import bassamalim.halala.R
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Sizes

/**
 * The evidence a web search gave for what a merchant is: "Found online: <the page's title>",
 * which opens the page in the browser so you can judge the match. In the neutral hint colour.
 */
@Composable
fun FoundOnline(title: String, url: String, modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    Text(
        text = stringResource(R.string.found_online, title),
        style = HalalaType.Label,
        color = HalalaColors.Info,
        textDecoration = TextDecoration.Underline,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .heightIn(min = Sizes.touchTarget)
            .clickable(role = Role.Button) { runCatching { uri.openUri(url) } }
    )
}
