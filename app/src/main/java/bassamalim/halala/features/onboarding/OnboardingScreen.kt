package bassamalim.halala.features.onboarding

import bassamalim.halala.core.ui.components.CurrencyText
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.halala.R
import bassamalim.halala.core.ui.components.Avatar
import bassamalim.halala.core.ui.components.ButtonKind
import bassamalim.halala.core.ui.components.HalalaButton
import bassamalim.halala.core.ui.components.HalalaCard
import bassamalim.halala.core.ui.components.HalalaTextField
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaNumbers
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Radius
import bassamalim.halala.core.ui.theme.Sizes
import bassamalim.halala.core.ui.theme.Spacing

private val SMS_PERMISSIONS = arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)

/**
 * First-run setup, from the onboarding boards: let Halala read bank SMS, name the accounts it
 * found in them ("We found 7 accounts in your messages"), then "Your history is in". The
 * permission step has no board and is built from the same parts.
 */
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val askPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted -> viewModel.onPermission(granted.values.all { it }) }
    // Already allowed (a second visit from Settings): straight to reading.
    LaunchedEffect(Unit) {
        val held = SMS_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (held) viewModel.onPermission(true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screen)
            .padding(top = Insets.screenTop, bottom = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.card)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StepDots(current = state.step.ordinal, count = OnboardingStep.entries.size)
            if (state.step != OnboardingStep.History)
                TextLink(stringResource(R.string.onboarding_skip), HalalaColors.TextMuted, viewModel::onLeaveClick)
        }

        when (state.step) {
            OnboardingStep.Permission -> PermissionStep(state) { askPermission.launch(SMS_PERMISSIONS) }
            OnboardingStep.Accounts -> AccountsStep(state, viewModel::onNameChange, viewModel::onContinueClick)
            OnboardingStep.History -> HistoryStep(state, viewModel::onBalanceChange, viewModel::onDoneClick)
        }
    }
}

@Composable
private fun ColumnScope.PermissionStep(state: OnboardingUiState, onAllow: () -> Unit) {
    Heading(stringResource(R.string.onboarding_permission_title), stringResource(R.string.onboarding_permission_body))
    if (state.permissionDenied)
        Text(stringResource(R.string.onboarding_permission_denied), style = HalalaType.Caption, color = HalalaColors.StateWarn)

    Spacer(Modifier.weight(1f))
    HalalaButton(
        text = stringResource(R.string.onboarding_permission_allow),
        onClick = onAllow,
        kind = ButtonKind.Primary,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ColumnScope.AccountsStep(
    state: OnboardingUiState,
    onNameChange: (String, String) -> Unit,
    onContinue: () -> Unit
) {
    if (state.rows.isEmpty()) {
        Heading(stringResource(R.string.onboarding_reading_title), stringResource(R.string.onboarding_reading_body, state.messages))
        return
    }

    Heading(
        pluralStringResource(R.plurals.onboarding_accounts_title, state.rows.size, state.rows.size),
        stringResource(R.string.onboarding_accounts_body)
    )

    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        state.rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(thickness = Sizes.border, color = HalalaColors.Line)
            Row(
                modifier = Modifier.padding(vertical = Insets.row),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Avatar(row.initial)
                Column(Modifier.width(Sizes.onboardingBank), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Text(row.bank, style = HalalaType.Label, color = HalalaColors.Text)
                    Text(
                        text = row.digits.ifEmpty { stringResource(R.string.onboarding_no_digits) },
                        style = HalalaNumbers.Meta,
                        color = HalalaColors.TextMuted
                    )
                }
                HalalaTextField(
                    value = row.name,
                    onValueChange = { onNameChange(row.key, it) },
                    placeholder = stringResource(R.string.onboarding_name_placeholder),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Text(
            text = stringResource(R.string.onboarding_same_name),
            style = HalalaType.Caption,
            color = HalalaColors.TextMuted,
            modifier = Modifier.padding(top = Spacing.sm)
        )
        state.alsoFound?.let {
            Text(
                text = stringResource(R.string.onboarding_also_found, it),
                style = HalalaType.Caption,
                color = HalalaColors.TextMuted,
                modifier = Modifier.padding(top = Spacing.sm)
            )
        }
    }

    HalalaButton(
        text = stringResource(R.string.onboarding_continue),
        onClick = onContinue,
        kind = ButtonKind.Primary,
        enabled = !state.isReading,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ColumnScope.HistoryStep(
    state: OnboardingUiState,
    onBalanceChange: (Long, String) -> Unit,
    onDone: () -> Unit
) {
    Heading(
        stringResource(if (state.isReading) R.string.onboarding_sorting_title else R.string.onboarding_history_title),
        stringResource(if (state.isReading) R.string.onboarding_sorting_body else R.string.onboarding_history_body)
    )

    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Stat(stringResource(R.string.onboarding_messages), state.messages, Modifier.weight(1f))
        Stat(stringResource(R.string.onboarding_transactions), state.transactions, Modifier.weight(1f))
        Stat(stringResource(R.string.onboarding_since), state.since, Modifier.weight(1f))
    }

    // Balances settle only once every message is filed, so they wait for the sorting.
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        if (!state.isReading && state.balances.isNotEmpty()) {
            Text(
                text = stringResource(R.string.onboarding_balances_body),
                style = HalalaType.Caption,
                color = HalalaColors.TextMuted,
                modifier = Modifier.padding(bottom = Spacing.xs)
            )
            state.balances.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(thickness = Sizes.border, color = HalalaColors.Line)
                Row(
                    modifier = Modifier.padding(vertical = Insets.row),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    Text(row.name, style = HalalaType.Label, color = HalalaColors.Text, modifier = Modifier.weight(1f))
                    HalalaTextField(
                        value = row.value,
                        onValueChange = { onBalanceChange(row.accountId, it) },
                        numeric = true,
                        isError = row.isInvalid,
                        modifier = Modifier.width(Sizes.onboardingBalance)
                    )
                    CurrencyText(row.currency, HalalaType.Caption, HalalaColors.TextMuted)
                }
            }
        }
    }

    HalalaButton(
        text = stringResource(R.string.onboarding_done),
        onClick = onDone,
        kind = ButtonKind.Primary,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun Heading(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(title, style = HalalaType.ScreenTitle)
        Text(body, style = HalalaType.Body, color = HalalaColors.TextMuted)
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    HalalaCard(modifier = modifier, label = label) {
        Text(value, style = HalalaNumbers.Amount, maxLines = 1)
    }
}

/** The board's progress dots: done and current in jade, the current one stretched. */
@Composable
private fun StepDots(current: Int, count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        repeat(count) { index ->
            Box(
                Modifier
                    .height(Sizes.stepDot)
                    .width(if (index == current) Sizes.stepDotCurrent else Sizes.stepDot)
                    .clip(Radius.pill)
                    .background(if (index <= current) HalalaColors.Accent else HalalaColors.Surface2)
            )
        }
    }
}

@Composable
private fun TextLink(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text = text,
        style = HalalaType.Body,
        color = color,
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = Sizes.touchTarget)
            .wrapContentHeight()
    )
}
