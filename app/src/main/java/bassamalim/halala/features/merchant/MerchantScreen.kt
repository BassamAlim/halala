package bassamalim.halala.features.merchant

import bassamalim.halala.core.ui.components.Skeleton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.halala.R
import bassamalim.halala.core.ai.LookupOutcome
import bassamalim.halala.core.enums.IdentifiedBy
import bassamalim.halala.core.ui.aliasMatchLabel
import bassamalim.halala.core.ui.components.Avatar
import bassamalim.halala.core.ui.components.ButtonKind
import bassamalim.halala.core.ui.components.ConfirmSheet
import bassamalim.halala.core.ui.components.FormField
import bassamalim.halala.core.ui.components.FoundOnline
import bassamalim.halala.core.ui.components.GroupLabel
import bassamalim.halala.core.ui.components.HalalaButton
import bassamalim.halala.core.ui.components.HalalaSheet
import bassamalim.halala.core.ui.components.HalalaTextField
import bassamalim.halala.core.ui.components.ListCard
import bassamalim.halala.core.ui.components.ListRow
import bassamalim.halala.core.ui.components.SearchField
import bassamalim.halala.core.ui.components.SummaryCard
import bassamalim.halala.core.ui.components.TopBar
import bassamalim.halala.core.ui.components.TransactionItemRow
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Spacing
import bassamalim.halala.core.enums.BusinessType
import bassamalim.halala.core.ui.businessTypeLabel
import bassamalim.halala.core.ui.identifiedLabel
import bassamalim.halala.core.ui.components.ChoiceSheet

/**
 * One merchant: what you call it, what was spent there, every way its bank writes it (each
 * saying how it got here), and its transactions. A spelling that joined it by mistake is taken
 * out from here, and a merchant that is another by a different name is merged from here. No
 * board draws this screen: it is Transaction detail's header, a summary card and Settings' list
 * card.
 */
@Composable
fun MerchantScreen(viewModel: MerchantViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screen)
            .padding(top = Insets.screenTop)
    ) {
        TopBar(
            title = stringResource(R.string.merchant),
            onBack = viewModel::onBackClick,
            actionLabel = stringResource(R.string.merchant_rename),
            actionEnabled = !state.isLoading,
            onAction = viewModel::onRenameClick
        )

        if (state.isLoading) {
            Skeleton()
            return@Column
        }

        // Cards are a card's gap apart; the transactions under them run on like a feed.
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
                    Avatar(initial = state.initial, merchantId = state.id)
                    Text(text = state.name, style = HalalaType.Title, textAlign = TextAlign.Center)
                }
            }

            item {
                SummaryCard(
                    label = stringResource(R.string.merchant_spent),
                    amount = state.spent,
                    currency = state.currency,
                    caption = if (state.count == 0) null
                    else pluralStringResource(R.plurals.review_cluster_meta, state.count, state.count, state.since),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.card)
                )
            }

            item {
                Column(
                    modifier = Modifier.padding(bottom = Spacing.card),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    GroupLabel(stringResource(R.string.merchant_is))
                    ListCard(Modifier.fillMaxWidth()) {
                        val identified = state.identifiedBy?.let { identifiedLabel(it, state.confidence) }
                        val filesUnder = state.filesUnder
                        ListRow(
                            title = state.businessType?.let { businessTypeLabel(it) }
                                ?: stringResource(R.string.merchant_is_unknown),
                            subtitle = when {
                                identified != null && filesUnder != null ->
                                    stringResource(R.string.meta_pair, identified, filesUnder)
                                else -> identified ?: filesUnder
                            },
                            onClick = viewModel::onBusinessTypeClick
                        )
                        ListRow(
                            title = stringResource(R.string.merchant_logo),
                            subtitle = state.website ?: stringResource(R.string.merchant_logo_none),
                            divider = true,
                            onClick = viewModel::onLogoClick
                        )
                    }
                    state.webUrl?.let { url -> FoundOnline(state.webTitle ?: url, url) }
                    if (state.canLookUp) {
                        val working = state.lookup?.working == true
                        HalalaButton(
                            text = stringResource(if (working) R.string.merchant_looking_up else R.string.merchant_look_up),
                            onClick = viewModel::onLookUpClick,
                            enabled = !working,
                            icon = R.drawable.ic_globe,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    state.lookup?.outcome?.let { outcome ->
                        Text(
                            text = stringResource(lookupLabel(outcome)),
                            style = HalalaType.Label,
                            color = HalalaColors.TextMuted
                        )
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier.padding(bottom = Spacing.card),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    GroupLabel(stringResource(R.string.merchant_spellings))
                    ListCard(Modifier.fillMaxWidth()) {
                        state.spellings.forEachIndexed { index, spelling ->
                            ListRow(
                                title = spelling.descriptor,
                                subtitle = stringResource(
                                    R.string.meta_pair,
                                    aliasMatchLabel(spelling.matchedBy),
                                    pluralStringResource(R.plurals.transaction_count, spelling.count, spelling.count)
                                ),
                                divider = index > 0,
                                onClick = if (state.canSplit) ({ viewModel.onSpellingClick(spelling) }) else null
                            )
                        }
                    }
                }
            }

            item {
                HalalaButton(
                    text = stringResource(R.string.merchant_merge),
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
        is MerchantSheet.Rename -> HalalaSheet(onDismiss = viewModel::onSheetDismiss) {
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

        is MerchantSheet.Logo -> HalalaSheet(onDismiss = viewModel::onSheetDismiss) {
            Text(text = stringResource(R.string.merchant_logo), style = HalalaType.Title)
            Text(
                text = stringResource(R.string.merchant_logo_body),
                style = HalalaType.Label,
                color = HalalaColors.TextMuted
            )
            FormField(
                label = stringResource(R.string.merchant_website),
                error = sheet.problem?.let { stringResource(logoProblemLabel(it)) }
            ) {
                HalalaTextField(
                    value = sheet.website,
                    onValueChange = viewModel::onWebsiteChange,
                    placeholder = stringResource(R.string.merchant_website_hint),
                    keyboardType = KeyboardType.Uri,
                    isError = sheet.problem != null,
                    imeAction = ImeAction.Done
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                HalalaButton(
                    text = stringResource(R.string.merchant_logo_remove),
                    onClick = viewModel::onLogoRemove,
                    enabled = !sheet.working,
                    modifier = Modifier.weight(1f)
                )
                HalalaButton(
                    text = stringResource(if (sheet.working) R.string.merchant_logo_fetching else R.string.save),
                    onClick = viewModel::onLogoSave,
                    enabled = !sheet.working,
                    kind = ButtonKind.Primary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        is MerchantSheet.Split -> ConfirmSheet(
            title = stringResource(R.string.merchant_split_title, sheet.spelling.descriptor, state.name),
            body = stringResource(R.string.merchant_split_body, state.name),
            confirmLabel = stringResource(R.string.merchant_split_confirm),
            dismissLabel = stringResource(R.string.cancel),
            onConfirm = viewModel::onSplitConfirm,
            onDismiss = viewModel::onSheetDismiss,
            destructive = false
        )

        is MerchantSheet.Merge -> HalalaSheet(onDismiss = viewModel::onSheetDismiss) {
            Text(text = stringResource(R.string.merchant_merge_title, state.name), style = HalalaType.Title)
            SearchField(
                value = sheet.query,
                onValueChange = viewModel::onMergeQueryChange,
                placeholder = stringResource(R.string.merchants_search),
                modifier = Modifier.fillMaxWidth()
            )
            if (state.mergeOptions.isEmpty()) {
                Text(
                    text = stringResource(R.string.merchant_merge_none),
                    style = HalalaType.Label,
                    color = HalalaColors.TextMuted
                )
            } else ListCard(Modifier.fillMaxWidth()) {
                state.mergeOptions.forEachIndexed { index, option ->
                    ListRow(
                        title = option.name,
                        subtitle = pluralStringResource(R.plurals.transaction_count, option.count, option.count),
                        divider = index > 0,
                        onClick = { viewModel.onMergePick(option) }
                    )
                }
            }
        }

        is MerchantSheet.ConfirmMerge -> ConfirmSheet(
            title = stringResource(R.string.merchant_merge_confirm_title, state.name, sheet.into.name),
            body = stringResource(R.string.merchant_merge_confirm_body, state.name, sheet.into.name),
            confirmLabel = stringResource(R.string.merchant_merge_confirm),
            dismissLabel = stringResource(R.string.cancel),
            onConfirm = viewModel::onMergeConfirm,
            onDismiss = viewModel::onSheetDismiss,
            destructive = false
        )

        MerchantSheet.BusinessType -> ChoiceSheet(
            title = stringResource(R.string.merchant_is_title, state.name),
            options = BusinessType.TAKEABLE,
            selected = state.businessType,
            label = { businessTypeLabel(it) },
            onPick = viewModel::onBusinessTypePick,
            onDismiss = viewModel::onSheetDismiss
        )

        is MerchantSheet.Found -> HalalaSheet(onDismiss = viewModel::onSheetDismiss) {
            val answer = sheet.found.answer
            Text(text = stringResource(R.string.merchant_lookup_found_title), style = HalalaType.Title)
            ListCard(Modifier.fillMaxWidth()) {
                ListRow(
                    title = businessTypeLabel(answer.type),
                    subtitle = stringResource(
                        R.string.meta_pair,
                        answer.name.ifBlank { state.name },
                        identifiedLabel(IdentifiedBy.AI, answer.confidence)
                    )
                )
            }
            sheet.found.url?.let { url -> FoundOnline(sheet.found.title ?: url, url) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                HalalaButton(
                    text = stringResource(R.string.merchant_lookup_reject),
                    onClick = viewModel::onSheetDismiss,
                    modifier = Modifier.weight(1f)
                )
                HalalaButton(
                    text = stringResource(R.string.merchant_lookup_accept),
                    onClick = viewModel::onLookupAccept,
                    kind = ButtonKind.Primary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        null -> Unit
    }
}

private fun logoProblemLabel(problem: LogoProblem): Int = when (problem) {
    LogoProblem.NotAWebsite -> R.string.merchant_website_invalid
    LogoProblem.NoneFound -> R.string.merchant_logo_not_found
    LogoProblem.Offline -> R.string.merchant_logo_offline
}

/** How looking it up went, in words. */
private fun lookupLabel(outcome: LookupOutcome): Int = when (outcome) {
    LookupOutcome.FOUND -> R.string.merchant_lookup_found_title
    LookupOutcome.NOTHING_NEW -> R.string.merchant_lookup_nothing_new
    LookupOutcome.WITHHELD -> R.string.merchant_lookup_withheld
    LookupOutcome.OFFLINE -> R.string.merchant_lookup_offline
    LookupOutcome.LIMITED -> R.string.merchant_lookup_limited
    LookupOutcome.FAILED -> R.string.merchant_lookup_failed
    LookupOutcome.UNAVAILABLE -> R.string.merchant_lookup_unavailable
}
