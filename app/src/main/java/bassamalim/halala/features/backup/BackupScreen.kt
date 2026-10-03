package bassamalim.halala.features.backup

import androidx.compose.ui.graphics.Color
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.halala.R
import bassamalim.halala.core.backup.BackupOutcome
import bassamalim.halala.core.models.BackupEvery
import bassamalim.halala.core.ui.components.ButtonKind
import bassamalim.halala.core.ui.components.ChoiceChips
import bassamalim.halala.core.ui.components.ConfirmSheet
import bassamalim.halala.core.ui.components.FormField
import bassamalim.halala.core.ui.components.HalalaButton
import bassamalim.halala.core.ui.components.HalalaSheet
import bassamalim.halala.core.ui.components.HalalaTextField
import bassamalim.halala.core.ui.components.ListCard
import bassamalim.halala.core.ui.components.ListRow
import bassamalim.halala.core.ui.components.TopBar
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Spacing

/**
 * Encrypted backups (no board: the system's list card, chips and sheet). A passphrase only you
 * know, a folder you choose, how often, and how many to keep.
 */
@Composable
fun BackupScreen(viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val messages = mapOf(
        BackupOutcome.DONE to stringResource(R.string.backup_done),
        BackupOutcome.NO_KEY to stringResource(R.string.backup_no_key),
        BackupOutcome.NO_FOLDER to stringResource(R.string.backup_no_folder),
        BackupOutcome.FAILED to stringResource(R.string.backup_failed)
    )
    val passphraseSet = stringResource(R.string.backup_passphrase_set)

    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.onFolderPicked(uri.toString())
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(
                when (event) {
                    is BackupEvent.Finished -> messages.getValue(event.outcome)
                    BackupEvent.PassphraseSet -> passphraseSet
                }
            )
        }
    }

    Scaffold(containerColor = Color.Transparent, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen)
                .padding(top = Insets.screenTop, bottom = Spacing.section),
            verticalArrangement = Arrangement.spacedBy(Spacing.section)
        ) {
            TopBar(title = stringResource(R.string.backup_title), onBack = viewModel::onBackClick)
            if (state.isLoading) return@Column

            Text(text = stringResource(R.string.backup_explain), style = HalalaType.Body, color = HalalaColors.TextMuted)

            ListCard(Modifier.fillMaxWidth()) {
                ListRow(
                    title = stringResource(if (state.hasPassphrase) R.string.backup_change_passphrase else R.string.backup_set_passphrase),
                    subtitle = stringResource(if (state.hasPassphrase) R.string.backup_passphrase_on else R.string.backup_passphrase_off),
                    onClick = viewModel::onPassphraseClick
                )
                ListRow(
                    title = stringResource(R.string.backup_folder),
                    subtitle = state.folderName ?: stringResource(if (state.hasFolder) R.string.backup_folder_gone else R.string.backup_folder_none),
                    divider = true,
                    onClick = { folderLauncher.launch(null) }
                )
            }

            if (state.hasPassphrase) {
                FormField(label = stringResource(R.string.backup_every)) {
                    ChoiceChips(
                        options = BackupEvery.entries,
                        selected = state.every,
                        label = {
                            stringResource(
                                when (it) {
                                    BackupEvery.OFF -> R.string.backup_every_off
                                    BackupEvery.DAILY -> R.string.backup_every_daily
                                    BackupEvery.WEEKLY -> R.string.backup_every_weekly
                                }
                            )
                        },
                        onSelect = viewModel::onEveryClick
                    )
                }
                FormField(label = stringResource(R.string.backup_keep)) {
                    ChoiceChips(
                        options = BackupViewModel.KEEPS,
                        selected = state.keep,
                        label = { stringResource(R.string.backup_keep_last, it) },
                        onSelect = viewModel::onKeepClick
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    HalalaButton(
                        text = stringResource(if (state.working) R.string.backup_working else R.string.backup_now),
                        onClick = viewModel::onBackUpNowClick,
                        kind = ButtonKind.Primary,
                        enabled = !state.working && state.hasFolder,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = state.lastLabel?.let { stringResource(R.string.backup_last, it) } ?: stringResource(R.string.backup_never),
                        style = HalalaType.Caption,
                        color = HalalaColors.TextMuted
                    )
                }
                HalalaButton(
                    text = stringResource(R.string.backup_turn_off),
                    onClick = viewModel::onTurnOffClick,
                    destructive = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Text(text = stringResource(R.string.backup_restore_hint), style = HalalaType.Caption, color = HalalaColors.TextMuted)
        }
    }

    state.form?.let { form ->
        HalalaSheet(viewModel::onFormDismiss) {
            Text(text = stringResource(R.string.backup_set_passphrase), style = HalalaType.Title)
            Text(text = stringResource(R.string.backup_passphrase_warning), style = HalalaType.Label, color = HalalaColors.TextMuted)
            FormField(
                label = stringResource(R.string.backup_passphrase),
                error = stringResource(R.string.backup_too_short, BackupViewModel.MIN_LENGTH).takeIf { form.problem == PassphraseProblem.TOO_SHORT }
            ) {
                HalalaTextField(value = form.first, onValueChange = viewModel::onFirstChange, secret = true, isError = form.problem == PassphraseProblem.TOO_SHORT)
            }
            FormField(
                label = stringResource(R.string.backup_passphrase_again),
                error = stringResource(R.string.backup_mismatch).takeIf { form.problem == PassphraseProblem.MISMATCH }
            ) {
                HalalaTextField(
                    value = form.second,
                    onValueChange = viewModel::onSecondChange,
                    secret = true,
                    isError = form.problem == PassphraseProblem.MISMATCH,
                    imeAction = ImeAction.Done,
                    onImeAction = viewModel::onPassphraseSave
                )
            }
            HalalaButton(
                text = stringResource(if (state.working) R.string.backup_working else R.string.save),
                onClick = viewModel::onPassphraseSave,
                kind = ButtonKind.Primary,
                enabled = !state.working,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (state.confirmingOff) ConfirmSheet(
        title = stringResource(R.string.backup_turn_off_title),
        body = stringResource(R.string.backup_turn_off_body),
        confirmLabel = stringResource(R.string.backup_turn_off),
        dismissLabel = stringResource(R.string.cancel),
        onConfirm = viewModel::onTurnOffConfirm,
        onDismiss = viewModel::onTurnOffDismiss
    )
}
