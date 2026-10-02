package bassamalim.halala.features.person

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import bassamalim.halala.R
import bassamalim.halala.core.enums.AmountTone
import bassamalim.halala.core.enums.LoanEventType
import bassamalim.halala.core.ui.components.ButtonKind
import bassamalim.halala.core.ui.components.HalalaButton
import bassamalim.halala.core.ui.components.HalalaCard
import bassamalim.halala.core.ui.components.ListRow
import bassamalim.halala.core.ui.components.ProgressBar
import bassamalim.halala.core.ui.components.appendCurrency
import bassamalim.halala.core.ui.components.currencyInlineContent
import bassamalim.halala.core.ui.kindLabel
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaNumbers
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Spacing

/**
 * The Person board's loan: what is still owed, of what was lent when and what was repaid, how
 * far it is paid back, the reminder and repayment buttons, and "This loan": what happened to it.
 */
@Composable
fun OpenLoan(loan: PersonLoan, person: String, viewModel: PersonViewModel) {
    val context = LocalContext.current
    val reminder = stringResource(R.string.loan_reminder_message, person, "${loan.remaining} ${loan.currency}", loan.lentOn)
    val share = stringResource(R.string.loan_send_reminder)

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = stringResource(if (loan.lent) R.string.loan_owes_you else R.string.loan_you_owe),
                style = HalalaType.Label,
                color = HalalaColors.TextMuted
            )
            Text(
                text = buildAnnotatedString {
                    append(loan.remaining)
                    appendCurrency(loan.currency)
                },
                style = HalalaNumbers.AmountXl,
                inlineContent = currencyInlineContent(HalalaColors.TextMuted)
            )
            Text(text = loanCaption(loan), style = HalalaType.Label, color = HalalaColors.TextMuted)
        }
        ProgressBar(progress = loan.progress)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            // Asking for it back is yours to do only when you lent it.
            if (loan.lent) HalalaButton(
                text = share,
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, reminder)
                    context.startActivity(Intent.createChooser(send, share))
                },
                kind = ButtonKind.Primary,
                modifier = Modifier.weight(1f)
            )
            HalalaButton(
                text = stringResource(R.string.loan_record_repayment),
                onClick = { viewModel.onRepayClick(loan.loanId) },
                modifier = Modifier.weight(1f)
            )
        }
        LoanEvents(loan, viewModel)
    }
}

/** A settled loan: what it was and when it was settled, then what happened to it. */
@Composable
fun SettledLoan(loan: PersonLoan, viewModel: PersonViewModel) {
    LoanEvents(loan, viewModel, label = stringResource(
        when {
            loan.forgiven -> R.string.loan_forgiven_on
            loan.lent -> R.string.loan_repaid_on
            else -> R.string.loan_you_repaid_on
        },
        loan.settledLabel.orEmpty()
    ))
}

@Composable
private fun LoanEvents(loan: PersonLoan, viewModel: PersonViewModel, label: String = stringResource(R.string.loan_this)) {
    HalalaCard(label = label, modifier = Modifier.fillMaxWidth()) {
        if (loan.open) ListRow(
            title = stringResource(R.string.loan_due),
            subtitle = loan.dueLabel ?: stringResource(R.string.loan_due_none),
            onClick = { viewModel.onDueClick(loan.loanId) }
        )
        loan.events.forEachIndexed { index, event ->
            val title = stringResource(
                when (event.type) {
                    LoanEventType.DISBURSEMENT -> if (loan.lent) R.string.loan_event_lent else R.string.loan_event_borrowed
                    LoanEventType.REPAYMENT -> R.string.loan_event_repayment
                    LoanEventType.FORGIVENESS -> R.string.loan_event_forgiven
                }
            )
            val kind = event.kind?.let { kindLabel(it) }
            val meta = listOfNotNull(event.day, kind, event.accountLabel).joinToString(" · ")
            val color = if (event.tone == AmountTone.Income) HalalaColors.Income else HalalaColors.Text
            ListRow(
                title = title,
                subtitle = meta,
                divider = loan.open || index > 0,
                trailing = {
                    Text(
                        text = event.amount,
                        style = HalalaNumbers.Amount,
                        color = color,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                },
                onClick = event.transactionId?.let { id -> { viewModel.onTransactionClick(id) } }
            )
        }
    }
}

/** "Of 1,500 lent on 12 Sep · 500 repaid · due 15 Oct". */
@Composable
private fun loanCaption(loan: PersonLoan): String {
    val parts = listOfNotNull(
        stringResource(if (loan.lent) R.string.loan_caption_lent else R.string.loan_caption_borrowed, loan.lentTotal, loan.lentOn),
        if (loan.hasRepaid) stringResource(R.string.loan_caption_repaid, loan.repaid) else null,
        loan.dueLabel?.let { stringResource(R.string.loan_due_on, it) }
    )
    return parts.joinToString(" · ")
}
