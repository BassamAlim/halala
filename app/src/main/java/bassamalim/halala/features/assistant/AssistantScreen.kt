package bassamalim.halala.features.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextAlign
import bassamalim.halala.core.ui.components.TopBar
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.halala.R
import bassamalim.halala.core.ui.components.HalalaCard
import bassamalim.halala.core.ui.components.HalalaChip
import bassamalim.halala.core.ui.components.ChipStyle
import bassamalim.halala.core.ui.components.HalalaTextField
import bassamalim.halala.core.ui.components.appendCurrency
import bassamalim.halala.core.ui.components.currencyInlineContent
import androidx.compose.ui.text.buildAnnotatedString
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaNumbers
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Radius
import bassamalim.halala.core.ui.theme.Sizes
import bassamalim.halala.core.ui.theme.Spacing

/**
 * Ask (the Assistant board, without the conversation): a question about your transactions and
 * its answer, worked out on the phone. Only the question goes to the AI, which writes the query.
 */
@Composable
fun AssistantScreen(viewModel: AssistantViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = Spacing.screen)
            .padding(top = Insets.screenTop, bottom = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        TopBar(title = stringResource(R.string.assistant_title), onBack = viewModel::onBackClick)

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.card)
        ) {
            val exchange = state.exchange
            if (exchange == null) {
                Text(text = stringResource(R.string.assistant_intro), style = HalalaType.Body, color = HalalaColors.TextMuted)
            } else {
                Question(exchange.question)
                ReplyCard(exchange.reply)
            }
        }

        val suggestions = listOf(
            stringResource(R.string.assistant_suggest_weekday),
            stringResource(R.string.assistant_suggest_average),
            stringResource(R.string.assistant_suggest_biggest),
            stringResource(R.string.assistant_suggest_months)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            items(suggestions) { HalalaChip(label = it, style = ChipStyle.Outline, onClick = { viewModel.onSuggestionClick(it) }) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            HalalaTextField(
                value = state.draft,
                onValueChange = viewModel::onDraftChange,
                placeholder = stringResource(R.string.assistant_ask),
                imeAction = ImeAction.Send,
                onImeAction = viewModel::onSendClick,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(Sizes.touchTarget)
                    .clip(Radius.md)
                    .background(if (state.busy) HalalaColors.Surface2 else HalalaColors.Accent)
                    .clickable(enabled = !state.busy, role = Role.Button, onClickLabel = stringResource(R.string.assistant_send), onClick = viewModel::onSendClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_send),
                    contentDescription = stringResource(R.string.assistant_send),
                    tint = if (state.busy) HalalaColors.TextMuted else HalalaColors.OnAccent,
                    modifier = Modifier.size(Sizes.iconSmall)
                )
            }
        }
    }
}

@Composable
private fun Question(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Text(
            text = text,
            style = HalalaType.Body,
            modifier = Modifier
                .fillMaxWidth(QUESTION_WIDTH)
                .wrapContentWidth(Alignment.End)
                .clip(Radius.lg)
                .background(HalalaColors.Surface2)
                .padding(horizontal = Spacing.card, vertical = Insets.row)
        )
    }
}

@Composable
private fun ReplyCard(reply: Reply) {
    HalalaCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        when (reply) {
            Reply.Thinking -> Text(text = stringResource(R.string.assistant_thinking), style = HalalaType.Body, color = HalalaColors.TextMuted)
            is Reply.Problem -> Text(text = stringResource(problemText(reply.problem)), style = HalalaType.Body, color = HalalaColors.TextMuted)
            is Reply.Table -> {
                val only = reply.rows.singleOrNull()?.singleOrNull()
                when {
                    reply.rows.isEmpty() -> Text(text = stringResource(R.string.assistant_nothing), style = HalalaType.Body, color = HalalaColors.TextMuted)
                    only != null -> Headline(only, reply.headings.first())
                    else -> {
                        TableRow(reply.headings.mapIndexed { i, heading -> Cell(heading, number = reply.rows.first()[i].number) }, heading = true)
                        reply.rows.forEach { TableRow(it, heading = false) }
                        if (reply.more) Text(text = stringResource(R.string.assistant_more, reply.rows.size), style = HalalaType.Caption, color = HalalaColors.TextMuted)
                    }
                }
                var shown by rememberSaveable(reply.sql) { mutableStateOf(false) }
                Text(
                    text = stringResource(R.string.assistant_how),
                    style = HalalaType.Label,
                    color = HalalaColors.TextMuted,
                    modifier = Modifier
                        .heightIn(min = Sizes.touchTarget)
                        .clickable(role = Role.Button) { shown = !shown }
                        .padding(vertical = Spacing.md)
                )
                if (shown) Text(text = reply.sql, style = HalalaNumbers.Meta, color = HalalaColors.TextMuted)
            }
        }
    }
}

@Composable
private fun Headline(cell: Cell, words: String) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = buildAnnotatedString {
                append(cell.text)
                if (cell.money && cell.currency != null) appendCurrency(cell.currency)
            },
            style = if (cell.number) HalalaNumbers.AmountLg else HalalaType.BodyStrong,
            inlineContent = currencyInlineContent(HalalaColors.TextMuted)
        )
        Text(text = words, style = HalalaType.Label, color = HalalaColors.TextMuted)
    }
}

@Composable
private fun TableRow(cells: List<Cell>, heading: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        cells.forEach { cell ->
            Text(
                text = cell.text,
                style = if (heading) HalalaType.Caption else if (cell.number) HalalaNumbers.Amount else HalalaType.Body,
                color = if (heading) HalalaColors.TextMuted else HalalaColors.Text,
                textAlign = if (cell.number) TextAlign.End else TextAlign.Start,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun problemText(problem: AskProblem) = when (problem) {
    AskProblem.NO_KEY -> R.string.assistant_no_key
    AskProblem.OFFLINE -> R.string.assistant_offline
    AskProblem.LIMITED -> R.string.assistant_limited
    AskProblem.UNREADABLE -> R.string.assistant_unreadable
    AskProblem.UNSUPPORTED -> R.string.assistant_unsupported
}

private const val QUESTION_WIDTH = 0.8f
