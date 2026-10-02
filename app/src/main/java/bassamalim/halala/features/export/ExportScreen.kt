package bassamalim.halala.features.export

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.halala.R
import bassamalim.halala.core.ui.components.ConfirmSheet
import bassamalim.halala.core.ui.components.ListCard
import bassamalim.halala.core.ui.components.ListRow
import bassamalim.halala.core.ui.components.TopBar
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Sizes
import bassamalim.halala.core.ui.theme.Spacing

/**
 * Backup and export. For now: CSV for spreadsheets and JSON for your own scripts, written to a
 * file you choose. Neither is encrypted, and the screen says so.
 */
@Composable
fun ExportScreen(viewModel: ExportViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val saved = stringResource(R.string.export_saved)
    val failed = stringResource(R.string.export_failed)
    val unreadable = stringResource(R.string.export_unreadable)
    val restored = stringResource(R.string.export_restored)
    val restoreFailed = stringResource(R.string.export_restore_failed)

    val write = { uri: android.net.Uri ->
        { bytes: ByteArray ->
            runCatching {
                checkNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(bytes) }
            }.isSuccess
        }
    }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> if (uri != null) viewModel.onCsvPicked(write(uri)) }

    val jsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) viewModel.onJsonPicked(write(uri)) }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.onRestorePicked {
            runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ExportEvent.Written -> if (event.succeeded) saved else failed
                ExportEvent.Unreadable -> unreadable
                is ExportEvent.Restored -> if (event.succeeded) restored else restoreFailed
            }.let { message ->
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    Scaffold(
        containerColor = HalalaColors.Bg,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Spacing.screen)
                .padding(top = Insets.screenTop, bottom = Spacing.section),
            verticalArrangement = Arrangement.spacedBy(Spacing.card)
        ) {
            TopBar(title = stringResource(R.string.export_title), onBack = viewModel::onBackClick)

            ListCard(Modifier.fillMaxWidth()) {
                ListRow(
                    title = stringResource(R.string.export_csv),
                    subtitle = stringResource(R.string.export_csv_summary),
                    leading = { ExportIcon() },
                    onClick = { if (!state.isWorking) csvLauncher.launch(viewModel.csvFileName()) }
                )
                ListRow(
                    title = stringResource(R.string.export_json),
                    subtitle = stringResource(R.string.export_json_summary),
                    divider = true,
                    leading = { ExportIcon() },
                    onClick = { if (!state.isWorking) jsonLauncher.launch(viewModel.jsonFileName()) }
                )
                ListRow(
                    title = stringResource(R.string.export_restore),
                    subtitle = stringResource(R.string.export_restore_summary),
                    divider = true,
                    leading = { ExportIcon() },
                    // Some file pickers know a .json only as plain text or bytes.
                    onClick = { if (!state.isWorking) restoreLauncher.launch(JSON_TYPES) }
                )
            }

            Text(
                text = stringResource(R.string.export_warning),
                style = HalalaType.Caption,
                color = HalalaColors.TextMuted
            )
        }
    }

    state.restore?.let { restore ->
        ConfirmSheet(
            title = stringResource(R.string.export_restore_title),
            body = stringResource(R.string.export_restore_body, restore.transactions, restore.accounts),
            confirmLabel = stringResource(R.string.export_restore_confirm),
            dismissLabel = stringResource(R.string.cancel),
            onConfirm = viewModel::onRestoreConfirm,
            onDismiss = viewModel::onRestoreDismiss
        )
    }
}

private val JSON_TYPES = arrayOf("application/json", "text/plain", "application/octet-stream")

@Composable
private fun ExportIcon() {
    Icon(
        painter = painterResource(R.drawable.ic_export),
        contentDescription = null,
        tint = HalalaColors.TextMuted,
        modifier = Modifier.size(Sizes.iconSmall)
    )
}
