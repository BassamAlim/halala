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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.halala.R
import bassamalim.halala.core.ui.components.GroupLabel
import bassamalim.halala.core.ui.components.ListCard
import bassamalim.halala.core.ui.components.ListRow
import bassamalim.halala.core.ui.components.TopBar
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Sizes
import bassamalim.halala.core.ui.theme.Spacing
import androidx.compose.ui.text.input.ImeAction
import bassamalim.halala.core.ui.components.HalalaTextField
import bassamalim.halala.core.ui.components.FormField
import bassamalim.halala.core.ui.components.ButtonKind
import bassamalim.halala.core.ai.IdentifyProblem

/**
 * Settings, from the Settings board, holding only the rows that are true today: accounts, bank
 * messages, categories, rules, recent changes, the review reminder, merchant identification,
 * backup and export, and the lock. Digests, web search and the usage cap join as they are built.
 */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
                title = stringResource(R.string.merchants),
                subtitle = stringResource(R.string.settings_merchants_summary),
                divider = true,
                onClick = viewModel::onMerchantsClick
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
        }

        Section(stringResource(R.string.settings_ai)) {
            ListRow(
                title = stringResource(R.string.ai_identification),
                subtitle = when {
                    !state.ai.enabled -> stringResource(R.string.ai_off)
                    state.ai.waiting > 0 -> pluralStringResource(R.plurals.ai_waiting, state.ai.waiting, state.ai.waiting)
                    else -> stringResource(R.string.ai_on_groq)
                },
                onClick = viewModel::onAiClick
            )
        }

        Section(stringResource(R.string.settings_privacy)) {
            ListRow(
                title = stringResource(R.string.export_title),
                subtitle = stringResource(R.string.settings_export_summary),
                onClick = viewModel::onExportClick
            )
            ListRow(
                title = stringResource(R.string.settings_lock),
                subtitle = pluralStringResource(R.plurals.settings_lock_summary, state.lockMinutes, state.lockMinutes),
                divider = true,
                leading = {
                    Icon(
                        painter = painterResource(R.drawable.ic_lock),
                        contentDescription = null,
                        tint = HalalaColors.TextMuted,
                        modifier = Modifier.size(Sizes.iconSmall)
                    )
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
    } else if (state.ai.isEditing) {
        AiSheet(state.ai, viewModel)
    }
}

/**
 * Merchant identification: on or off, the Groq key, and what went wrong last. It can only be
 * turned on with a key; forgetting the key turns it off.
 */
@Composable
private fun AiSheet(ai: AiSettings, viewModel: SettingsViewModel) {
    HalalaSheet(onDismiss = viewModel::onAiDismiss) {
        Text(text = stringResource(R.string.ai_identification), style = HalalaType.Title)
        Text(text = stringResource(R.string.ai_hint), style = HalalaType.Body, color = HalalaColors.TextMuted)

        ChoiceChips(
            options = listOf(false, true),
            selected = ai.enabled,
            label = { stringResource(if (it) R.string.ai_on else R.string.ai_off) },
            onSelect = viewModel::onAiEnabledPick,
            enabled = ai.hasKey
        )

        FormField(
            label = stringResource(R.string.ai_key),
            hint = stringResource(if (ai.hasKey) R.string.ai_key_saved else R.string.ai_key_hint)
        ) {
            HalalaTextField(
                value = ai.keyDraft.orEmpty(),
                onValueChange = viewModel::onKeyChange,
                secret = true,
                imeAction = ImeAction.Done
            )
        }
        HalalaButton(
            text = stringResource(R.string.ai_save_key),
            onClick = viewModel::onSaveKeyClick,
            kind = ButtonKind.Primary,
            enabled = !ai.keyDraft.isNullOrBlank(),
            modifier = Modifier.fillMaxWidth()
        )
        if (ai.hasKey) {
            HalalaButton(
                text = stringResource(R.string.ai_forget_key),
                onClick = viewModel::onForgetKeyClick,
                destructive = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (ai.enabled && ai.waiting > 0) {
            HalalaButton(
                text = stringResource(R.string.ai_identify_now),
                onClick = viewModel::onIdentifyNowClick,
                modifier = Modifier.fillMaxWidth()
            )
        }

        val note = when {
            !ai.hasKey -> R.string.ai_key_missing
            !ai.enabled -> null
            else -> when (ai.problem) {
                IdentifyProblem.KEY -> R.string.ai_error_key
                IdentifyProblem.UNREACHABLE -> R.string.ai_error_network
                IdentifyProblem.LIMITED -> R.string.ai_error_limit
                IdentifyProblem.REJECTED -> R.string.ai_error_rejected
                null -> null
            }
        }
        note?.let { Text(text = stringResource(it), style = HalalaType.Caption, color = HalalaColors.Info) }
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
