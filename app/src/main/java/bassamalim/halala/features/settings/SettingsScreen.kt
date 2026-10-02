package bassamalim.halala.features.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import bassamalim.halala.core.models.ReminderMode
import bassamalim.halala.core.ui.components.ChoiceChips
import bassamalim.halala.core.ui.components.HalalaButton
import bassamalim.halala.core.ui.components.HalalaSheet
import bassamalim.halala.core.ui.components.TimeDialog
import bassamalim.halala.core.ui.dayOfWeekLabel
import java.time.DayOfWeek
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.halala.R
import bassamalim.halala.features.lock.canAuthenticate
import bassamalim.halala.features.lock.findFragmentActivity
import bassamalim.halala.features.lock.promptForUnlock
import androidx.compose.foundation.layout.FlowRow
import bassamalim.halala.core.ui.components.HalalaChip
import bassamalim.halala.core.ui.components.ChipStyle
import bassamalim.halala.core.ui.components.rememberNotificationAsk
import bassamalim.halala.core.domain.DigestKind
import bassamalim.halala.core.ui.components.GroupLabel
import bassamalim.halala.core.ui.components.ListCard
import bassamalim.halala.core.ui.components.ListRow
import bassamalim.halala.core.ui.components.TopBar
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Spacing

/**
 * Settings, from the Settings board, holding only the rows that are true today: accounts, bank
 * messages, categories, rules, recent changes, the review reminder, and backup and export. What
 * is always on (the lock, merchant identification) has no row. Digests, web search and the usage
 * cap join as they are built.
 */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findFragmentActivity()
    val showAmountsTitle = stringResource(R.string.settings_show_amounts)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen)
            .padding(top = Insets.screenTop, bottom = Spacing.section),
        verticalArrangement = Arrangement.spacedBy(Spacing.card)
    ) {
        TopBar(title = stringResource(R.string.settings), onBack = viewModel::onBackClick)

        Section(stringResource(R.string.settings_money)) {
            ListRow(
                title = stringResource(R.string.accounts),
                subtitle = stringResource(
                    R.string.settings_accounts_summary,
                    pluralStringResource(R.plurals.account_count, state.accountCount, state.accountCount),
                    pluralStringResource(R.plurals.bank_count, state.bankCount, state.bankCount)
                ),
                onClick = viewModel::onAccountsClick
            )
            ListRow(
                title = stringResource(R.string.settings_messages),
                subtitle = stringResource(R.string.settings_messages_summary),
                divider = true,
                onClick = viewModel::onMessagesClick
            )
            ListRow(
                title = stringResource(R.string.categories),
                subtitle = stringResource(R.string.settings_categories_summary),
                divider = true,
                onClick = viewModel::onCategoriesClick
            )
            ListRow(
                title = stringResource(R.string.rules),
                subtitle = stringResource(R.string.settings_rules_summary),
                divider = true,
                onClick = viewModel::onRulesClick
            )
            ListRow(
                title = stringResource(R.string.history),
                subtitle = stringResource(R.string.settings_history_summary),
                divider = true,
                onClick = viewModel::onHistoryClick
            )
        }

        Section(stringResource(R.string.settings_reminders)) {
            ListRow(
                title = stringResource(R.string.reminder_review),
                subtitle = when (state.reminder.mode) {
                    ReminderMode.OFF -> stringResource(R.string.reminder_off)
                    ReminderMode.DAILY -> stringResource(R.string.reminder_daily_at, state.reminderTime)
                    ReminderMode.WEEKLY ->
                        stringResource(R.string.reminder_weekly_at, dayOfWeekLabel(state.reminder.day), state.reminderTime)
                },
                onClick = viewModel::onReminderClick
            )
            ListRow(
                title = stringResource(R.string.digests),
                subtitle = if (state.digests.isEmpty()) stringResource(R.string.reminder_off)
                else DigestKind.entries.filter { it in state.digests }.map { stringResource(digestLabel(it)) }.joinToString(", "),
                divider = true,
                onClick = viewModel::onDigestsClick
            )
        }

        Section(stringResource(R.string.settings_privacy)) {
            ListRow(
                title = stringResource(R.string.export_title),
                subtitle = stringResource(R.string.settings_export_summary),
                onClick = viewModel::onExportClick
            )
            ListRow(
                title = stringResource(R.string.settings_hide_amounts),
                subtitle = stringResource(
                    if (state.hideAmounts) R.string.settings_hide_amounts_on else R.string.settings_hide_amounts_off
                ),
                divider = true,
                onClick = {
                    when {
                        !state.hideAmounts -> viewModel.onHideAmounts(true)
                        // No screen lock at all: as with the lock, you can never lock yourself out.
                        activity == null || !activity.canAuthenticate() -> viewModel.onHideAmounts(false)
                        else -> activity.promptForUnlock(showAmountsTitle) { viewModel.onHideAmounts(false) }
                    }
                }
            )
        }

        Text(
            text = stringResource(R.string.settings_version, state.version),
            style = HalalaType.Caption,
            color = HalalaColors.TextMuted
        )
    }

    if (state.isPickingReminderTime) {
        TimeDialog(
            time = state.reminder.time,
            onPicked = viewModel::onReminderTimePicked,
            onDismiss = viewModel::onReminderTimeDismiss
        )
    } else if (state.isEditingReminder) {
        ReminderSheet(state, viewModel)
    } else if (state.isEditingDigests) {
        DigestsSheet(state, viewModel)
    }
}

private fun digestLabel(kind: DigestKind) = when (kind) {
    DigestKind.WEEK -> R.string.digest_weekly
    DigestKind.MONTH -> R.string.digest_monthly
    DigestKind.YEAR -> R.string.digest_yearly
}

/** Which digests to be told about; each arrives the morning after its period ends. */
@Composable
private fun DigestsSheet(state: SettingsUiState, viewModel: SettingsViewModel) {
    val askToNotify = rememberNotificationAsk()
    HalalaSheet(onDismiss = viewModel::onDigestsDismiss) {
        Text(text = stringResource(R.string.digests), style = HalalaType.Title)
        Text(text = stringResource(R.string.digests_settings_hint), style = HalalaType.Body, color = HalalaColors.TextMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            DigestKind.entries.forEach { kind ->
                HalalaChip(
                    label = stringResource(digestLabel(kind)),
                    style = if (kind in state.digests) ChipStyle.On else ChipStyle.Outline,
                    onClick = {
                        if (kind !in state.digests) askToNotify()
                        viewModel.onDigestToggle(kind)
                    }
                )
            }
        }
        HalalaButton(
            text = stringResource(R.string.digests_past),
            onClick = viewModel::onPastDigestsClick,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * How often, which day, and when. Turning it on asks for the notification permission (Android
 * 13 and later); if that is refused the sheet says so, since the reminder would never show.
 */
@Composable
private fun ReminderSheet(state: SettingsUiState, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }

    HalalaSheet(onDismiss = viewModel::onReminderDismiss) {
        Text(text = stringResource(R.string.reminder_review), style = HalalaType.Title)
        Text(
            text = stringResource(R.string.reminder_hint),
            style = HalalaType.Body,
            color = HalalaColors.TextMuted
        )

        ChoiceChips(
            options = ReminderMode.entries,
            selected = state.reminder.mode,
            label = {
                stringResource(
                    when (it) {
                        ReminderMode.OFF -> R.string.reminder_off
                        ReminderMode.DAILY -> R.string.reminder_daily
                        ReminderMode.WEEKLY -> R.string.reminder_weekly
                    }
                )
            },
            onSelect = { mode ->
                if (mode != ReminderMode.OFF && !allowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                viewModel.onReminderModePick(mode)
            }
        )

        if (state.reminder.mode == ReminderMode.WEEKLY) {
            ChoiceChips(
                options = DayOfWeek.entries,
                selected = state.reminder.day,
                label = { dayOfWeekLabel(it, short = true) },
                onSelect = viewModel::onReminderDayPick
            )
        }

        if (state.reminder.mode != ReminderMode.OFF) {
            HalalaButton(
                text = state.reminderTime,
                onClick = viewModel::onReminderTimeClick,
                modifier = Modifier.fillMaxWidth()
            )
            if (!allowed) {
                Text(
                    text = stringResource(R.string.reminder_blocked),
                    style = HalalaType.Caption,
                    color = HalalaColors.Info
                )
            }
        }
    }
}

@Composable
private fun Section(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        GroupLabel(label)
        ListCard(Modifier.fillMaxWidth()) { content() }
    }
}
