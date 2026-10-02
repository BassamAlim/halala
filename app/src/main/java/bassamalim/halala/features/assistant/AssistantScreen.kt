package bassamalim.halala.features.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.halala.R
import bassamalim.halala.core.ui.businessTypeLabel
import bassamalim.halala.core.ui.components.Avatar
import bassamalim.halala.core.enums.AmountTone
import bassamalim.halala.core.ui.components.HalalaCard
import bassamalim.halala.core.ui.components.HalalaChip
import bassamalim.halala.core.ui.components.ChipStyle
import bassamalim.halala.core.ui.components.HalalaTextField
import bassamalim.halala.core.ui.components.ScreenTitle
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
 * The Assistant board: your questions and the answers worked out on the phone, suggestions, and
 * the box to ask in. Only the question goes to the AI, which says which query to run.
 */
@Composable
fun AssistantScreen(viewModel: AssistantViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    LaunchedEffect(state.exchanges.size, state.busy) {
        if (state.exchanges.isNotEmpty()) list.animateScrollToItem(state.exchanges.size)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = Spacing.screen)
            .padding(top = Insets.screenTop, bottom = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        ScreenTitle(stringResource(R.string.tab_assistant)) {
            Text(
                text = stringResource(R.string.digests),
                style = HalalaType.Body,
                color = HalalaColors.Accent,
                modifier = Modifier
                    .heightIn(min = Sizes.touchTarget)
                    .clickable(role = Role.Button, onClick = viewModel::onDigestsClick)
                    .padding(vertical = Spacing.md)
            )
        }

        LazyColumn(
            state = list,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.card),
            contentPadding = PaddingValues(bottom = Spacing.xs)
        ) {
            item {
                if (state.exchanges.isEmpty()) Text(
                    text = stringResource(R.string.assistant_intro),
                    style = HalalaType.Body,
                    color = HalalaColors.TextMuted
                )
            }
            state.exchanges.forEach { exchange ->
                item(key = "q${exchange.id}") { Question(exchange.question) }
                item(key = "a${exchange.id}") { ReplyCard(exchange.reply, state.currency) }
            }
        }

        val suggestions = listOf(
            stringResource(R.string.assistant_suggest_afford),
            stringResource(R.string.assistant_suggest_bills),
            stringResource(R.string.assistant_suggest_owed),
            stringResource(R.string.assistant_suggest_month)
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
private fun ReplyCard(reply: Reply, currency: String) {
    HalalaCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        when (reply) {
            Reply.Thinking -> Text(text = stringResource(R.string.assistant_thinking), style = HalalaType.Body, color = HalalaColors.TextMuted)
            is Reply.Problem -> Text(text = stringResource(problemText(reply.problem)), style = HalalaType.Body, color = HalalaColors.TextMuted)
            is Reply.Spending -> {
                if (reply.unknown != null) Text(text = stringResource(R.string.assistant_unknown_topic, reply.unknown), style = HalalaType.Label, color = HalalaColors.Info)
                Headline(reply.total, currency, pluralStringResource(R.plurals.assistant_spent, reply.count, reply.count, periodText(reply.period)))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    HalalaChip(
                        label = when (val about = reply.about) {
                            AboutLabel.All -> stringResource(R.string.assistant_all_spending)
                            is AboutLabel.Category -> stringResource(R.string.assistant_category, about.name)
                            is AboutLabel.Merchant -> stringResource(R.string.assistant_merchant, about.name)
                            is AboutLabel.Type -> stringResource(R.string.assistant_type, businessTypeLabel(about.type))
                        }
                    )
                    HalalaChip(label = periodText(reply.period))
                }
                if (reply.bars.isNotEmpty()) Bars(reply.bars)
                val notes = listOfNotNull(
                    reply.top?.let { (name, share) -> stringResource(R.string.assistant_top, name, share) },
                    reply.highest?.let { stringResource(R.string.assistant_highest, it) }
                )
                if (notes.isNotEmpty()) Text(text = notes.joinToString(" "), style = HalalaType.Label, color = HalalaColors.TextMuted)
            }
            is Reply.Income -> Headline(reply.total, currency, pluralStringResource(R.plurals.assistant_came_in, reply.count, reply.count, periodText(reply.period)))
            is Reply.Bills -> {
                Text(text = stringResource(R.string.assistant_bills, periodText(reply.period)), style = HalalaType.Label, color = HalalaColors.TextMuted)
                if (reply.rows.isEmpty()) Text(text = stringResource(R.string.assistant_no_bills), style = HalalaType.Body, color = HalalaColors.TextMuted)
                reply.rows.forEach { (name, amount) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = name, style = HalalaType.Body, modifier = Modifier.weight(1f))
                        Text(text = amount, style = HalalaNumbers.Amount)
                    }
                }
            }
            is Reply.Owing -> {
                if (reply.toYou.isEmpty()) Text(text = stringResource(R.string.assistant_no_one_owes), style = HalalaType.Label, color = HalalaColors.TextMuted)
                reply.toYou.forEach { OwedLine(it, toYou = true) }
                if (reply.byYou.isEmpty()) Text(text = stringResource(R.string.assistant_you_owe_no_one), style = HalalaType.Label, color = HalalaColors.TextMuted)
                else {
                    Text(text = stringResource(R.string.assistant_you_owe), style = HalalaType.Label, color = HalalaColors.TextMuted)
                    reply.byYou.forEach { OwedLine(it, toYou = false) }
                }
            }
            is Reply.Afford -> {
                Text(
                    text = stringResource(
                        if (reply.monthly) R.string.assistant_afford_monthly else R.string.assistant_afford_once,
                        reply.amount, reply.on
                    ),
                    style = HalalaType.Label,
                    color = HalalaColors.TextMuted
                )
                Text(
                    text = stringResource(if (reply.ok) R.string.assistant_afford_yes else R.string.assistant_afford_no),
                    style = HalalaType.BodyStrong,
                    color = if (reply.ok) HalalaColors.Text else HalalaColors.StateOver
                )
                Text(text = stringResource(R.string.assistant_afford_lowest, reply.lowest, reply.lowestOn), style = HalalaType.Label, color = HalalaColors.TextMuted)
            }
            is Reply.Balance -> Headline(reply.total, currency, pluralStringResource(R.plurals.assistant_balance, reply.accounts, reply.accounts))
        }
    }
}

@Composable
private fun Headline(amount: String, currency: String, words: String) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = buildAnnotatedString {
                append(amount)
                appendCurrency(currency)
            },
            style = HalalaNumbers.AmountLg,
            inlineContent = currencyInlineContent(HalalaColors.TextMuted)
        )
        Text(text = words, style = HalalaType.Label, color = HalalaColors.TextMuted)
    }
}

@Composable
private fun Bars(bars: List<Bar>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        bars.forEach { bar ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Box(Modifier.height(BAR_HEIGHT), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier
                            .widthIn(max = Sizes.iconSmall)
                            .fillMaxWidth()
                            .height(BAR_HEIGHT * bar.fraction.coerceIn(0f, 1f))
                            .clip(Radius.xs)
                            .background(HalalaColors.Accent)
                    )
                }
                Text(text = bar.label, style = HalalaType.Caption, color = HalalaColors.TextMuted)
            }
        }
    }
}

@Composable
private fun OwedLine(row: OwedRow, toYou: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Avatar(initial = row.initial, tone = if (toYou) AmountTone.Income else AmountTone.Spending)
        Column(Modifier.weight(1f)) {
            Text(text = row.name, style = HalalaType.BodyStrong)
            val detail = row.due?.let { stringResource(R.string.assistant_due, it) } ?: row.since?.let { stringResource(R.string.assistant_since, it) }
            detail?.let { Text(text = it, style = HalalaType.Caption, color = HalalaColors.TextMuted) }
        }
        Text(text = row.amount, style = HalalaNumbers.Amount, color = if (toYou) HalalaColors.Income else HalalaColors.Text)
    }
}

@Composable
private fun periodText(period: Period) =
    if (period.to == null) stringResource(R.string.assistant_since, period.from) else stringResource(R.string.assistant_between, period.from, period.to)

private fun problemText(problem: AskProblem) = when (problem) {
    AskProblem.NO_KEY -> R.string.assistant_no_key
    AskProblem.OFFLINE -> R.string.assistant_offline
    AskProblem.LIMITED -> R.string.assistant_limited
    AskProblem.UNREADABLE -> R.string.assistant_unreadable
    AskProblem.UNSUPPORTED -> R.string.assistant_unsupported
    AskProblem.NO_AMOUNT -> R.string.assistant_no_amount
}

private const val QUESTION_WIDTH = 0.8f
private val BAR_HEIGHT = Sizes.touchTarget
