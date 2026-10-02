package bassamalim.halala.features.categories

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.halala.R
import bassamalim.halala.core.enums.ExpenseType
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
import bassamalim.halala.core.ui.expenseTypeLabel
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Spacing

/**
 * Your categories: add one, or remove one. No board draws this screen, so it is Settings' list
 * card and the app's form parts. Renaming and two levels ("Food › Delivery") come later.
 */
@Composable
fun CategoriesScreen(viewModel: CategoriesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen)
            .padding(top = Insets.screenTop, bottom = Spacing.section),
        verticalArrangement = Arrangement.spacedBy(Spacing.card)
    ) {
        TopBar(
            title = stringResource(R.string.categories),
            onBack = viewModel::onBackClick,
            actionLabel = stringResource(R.string.add),
            actionEnabled = !state.isLoading,
            onAction = viewModel::onAddClick
        )

        if (state.isLoading) return@Column

        if (state.categories.isNotEmpty()) {
            ListCard(Modifier.fillMaxWidth()) {
                state.categories.forEachIndexed { index, category ->
                    val uses = pluralStringResource(R.plurals.transaction_count, category.uses, category.uses)
                    ListRow(
                        title = category.name,
                        subtitle = category.expenseType
                            ?.let { stringResource(R.string.meta_pair, expenseTypeLabel(it), uses) }
                            ?: uses,
                        divider = index > 0,
                        onClick = { viewModel.onDeleteClick(category.id) }
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.categories_hint),
            style = HalalaType.Caption,
            color = HalalaColors.TextMuted
        )
    }

    state.adding?.let { new ->
        HalalaSheet(onDismiss = viewModel::onAddDismiss) {
            Text(text = stringResource(R.string.category_new), style = HalalaType.Title)

            FormField(
                label = stringResource(R.string.category_name),
                error = when (new.problem) {
                    CategoryProblem.NameMissing -> stringResource(R.string.category_name_missing)
                    CategoryProblem.NameTaken -> stringResource(R.string.category_name_taken)
                    null -> null
                }
            ) {
                HalalaTextField(
                    value = new.name,
                    onValueChange = viewModel::onNameChange,
                    isError = new.problem != null,
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                )
            }

            FormField(label = stringResource(R.string.expense_type), hint = stringResource(R.string.optional)) {
                ChoiceChips(
                    options = ExpenseType.entries,
                    selected = new.expenseType,
                    label = { expenseTypeLabel(it) },
                    onSelect = viewModel::onTypeClick
                )
            }

            HalalaButton(
                text = stringResource(R.string.add),
                onClick = viewModel::onSaveClick,
                kind = ButtonKind.Primary,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    state.deleting?.let { category ->
        ConfirmSheet(
            title = stringResource(R.string.category_delete_title, category.name),
            body = if (category.uses == 0) stringResource(R.string.category_delete_unused)
            else pluralStringResource(R.plurals.category_delete_body, category.uses, category.uses),
            confirmLabel = stringResource(R.string.delete),
            dismissLabel = stringResource(R.string.keep),
            onConfirm = viewModel::onDeleteConfirm,
            onDismiss = viewModel::onDeleteDismiss
        )
    }
}
