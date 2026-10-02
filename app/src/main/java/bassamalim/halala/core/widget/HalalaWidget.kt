package bassamalim.halala.core.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.action.clickable
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import bassamalim.halala.R
import bassamalim.halala.core.Activity
import bassamalim.halala.core.Globals
import bassamalim.halala.core.data.repositories.BudgetsRepository
import bassamalim.halala.core.data.repositories.TransactionsRepository
import bassamalim.halala.core.domain.BudgetState
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.domain.Rules
import bassamalim.halala.core.enums.BudgetScope
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaNumbers
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Radius
import bassamalim.halala.core.ui.theme.Sizes
import bassamalim.halala.core.ui.theme.Spacing
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

/** What the widget shows, already formatted: this cycle's spending against the total budget, and the inbox. */
data class WidgetModel(val spent: String, val limit: String?, val progress: Float, val state: BudgetState, val toReview: Int)

/**
 * The spec's home-screen widget: this cycle's spending against your total budget (or alone,
 * without one), how many merchants wait in Review, and "+ Cash". Tapping it opens the app,
 * which asks for the lock as always; so does "+ Cash", then the form follows.
 */
class HalalaWidget : GlanceAppWidget() {

    @EntryPoint @InstallIn(SingletonComponent::class)
    interface Data {
        fun budgets(): BudgetsRepository
        fun transactions(): TransactionsRepository
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = EntryPointAccessors.fromApplication(context.applicationContext, Data::class.java)
        val model = modelOf(data)
        provideContent { GlanceTheme { Content(context, model) } }
    }

    private suspend fun modelOf(data: Data): WidgetModel {
        val c = Globals.PRIMARY_CURRENCY
        val overview = data.budgets().observeOverview(c).first()
        val total = overview.statuses.firstOrNull { it.budget.scope == BudgetScope.TOTAL && it.budget.currency == c }
        val spent = total?.spentMinor ?: overview.spentMinor
        return WidgetModel(
            spent = Money.format(spent, c, decimals = false),
            limit = total?.let { Money.format(it.limitMinor, c, decimals = false) },
            progress = total?.let { if (it.limitMinor > 0) (it.spentMinor.toFloat() / it.limitMinor).coerceIn(0f, 1f) else 1f } ?: 0f,
            state = total?.state ?: BudgetState.OK,
            toReview = Rules.clusters(data.transactions().observeAll().first()).size
        )
    }

    @Composable
    private fun Content(context: Context, model: WidgetModel) {
        val open = Intent(context, Activity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val quickAdd = Intent(open).setAction(QuickAddRequest.ACTION)
        val muted = ColorProvider(HalalaColors.TextMuted)
        val text = ColorProvider(HalalaColors.Text)
        val bar = when (model.state) {
            BudgetState.OK -> HalalaColors.StateOk
            BudgetState.WARN -> HalalaColors.StateWarn
            BudgetState.OVER -> HalalaColors.StateOver
        }
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(HalalaColors.Surface)
                .cornerRadius(Radius.lgSize)
                .padding(Spacing.card)
                .clickable(actionStartActivity(open)),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                Text(
                    text = context.getString(R.string.widget_this_cycle),
                    style = TextStyle(color = muted, fontSize = HalalaType.Caption.fontSize),
                    modifier = GlanceModifier.defaultWeight()
                )
                if (model.toReview > 0) Text(
                    text = context.resources.getQuantityString(R.plurals.widget_to_review, model.toReview, model.toReview),
                    style = TextStyle(color = ColorProvider(HalalaColors.Accent), fontSize = HalalaType.Caption.fontSize, fontWeight = FontWeight.Medium)
                )
            }
            Spacer(GlanceModifier.height(Spacing.xs))
            Row(verticalAlignment = Alignment.Vertical.Bottom) {
                Text(text = model.spent, style = TextStyle(color = text, fontSize = HalalaNumbers.AmountLg.fontSize, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace))
                Spacer(GlanceModifier.width(Spacing.xs))
                Text(
                    text = model.limit?.let { context.getString(R.string.widget_of, it, Globals.PRIMARY_CURRENCY) } ?: Globals.PRIMARY_CURRENCY,
                    style = TextStyle(color = muted, fontSize = HalalaType.Caption.fontSize)
                )
            }
            if (model.limit != null) {
                Spacer(GlanceModifier.height(Spacing.sm))
                LinearProgressIndicator(
                    progress = model.progress,
                    modifier = GlanceModifier.fillMaxWidth().height(Sizes.progress),
                    color = ColorProvider(bar),
                    backgroundColor = ColorProvider(HalalaColors.Line)
                )
            }
            Spacer(GlanceModifier.height(Insets.row))
            Box(
                modifier = GlanceModifier
                    .height(Sizes.pill)
                    .padding(horizontal = Spacing.card)
                    .background(HalalaColors.Accent)
                    .cornerRadius(Radius.smSize)
                    .clickable(actionStartActivity(quickAdd)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = context.getString(R.string.widget_cash),
                    style = TextStyle(color = ColorProvider(HalalaColors.OnAccent), fontSize = HalalaType.Label.fontSize, fontWeight = FontWeight.Medium)
                )
            }
        }
    }

    companion object {
        /** Redraws every Halala widget with what the ledger says now. */
        suspend fun refresh(context: Context) = runCatching { HalalaWidget().updateAll(context) }
    }
}

class HalalaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HalalaWidget()
}

