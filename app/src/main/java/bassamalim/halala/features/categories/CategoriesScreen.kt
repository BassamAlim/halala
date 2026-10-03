package bassamalim.halala.features.categories

import bassamalim.halala.core.ui.components.Skeleton
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
import bassamalim.halala.core.enums.BusinessType
import bassamalim.halala.core.enums.ExpenseType
import bassamalim.halala.core.ui.businessTypeLabel
import bassamalim.halala.core.ui.components.ButtonKind
import bassamalim.halala.core.ui.components.ChoiceChips
import bassamalim.halala.core.ui.components.ConfirmSheet
import bassamalim.halala.core.ui.components.FormField
import bassamalim.halala.core.ui.components.HalalaButton
import bassamalim.halala.core.ui.components.HalalaSheet
import bassamalim.halala.core.ui.components.HalalaTextField
import bassamalim.halala.core.ui.components.ListCard
import bassamalim.halala.core.ui.components.ListRow
import bassamalim.halala.core.ui.components.MultiChoiceChips
import bassamalim.halala.core.ui.components.TopBar
import bassamalim.halala.core.ui.expenseTypeLabel
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Spacing

/**
 * Your categories: add one, rename one, choose the business types it takes, or remove one. No
 * board draws this screen, so it is Settings' list card and the app's form parts.
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

        if (state.isLoading) {
            Skeleton()
            return@Column
        }

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
                        onClick = { viewModel.onCategoryClick(category) }
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

    state.form?.let { form ->
        HalalaSheet(onDismiss = viewModel::onFormDismiss) {
            // Every business type is offered, so the form scrolls inside its sheet.
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Text(
                    text = stringResource(if (form.id == null) R.string.category_new else R.string.category_edit),
                    style = HalalaType.Title
                )

                FormField(
                    label = stringResource(R.string.category_name),
                    error = when (form.problem) {
                        CategoryProblem.NameMissing -> stringResource(R.string.category_name_missing)
                        CategoryProblem.NameTaken -> stringResource(R.string.category_name_taken)
                        null -> null
                    }
                ) {
                    HalalaTextField(
                        value = form.name,
                        onValueChange = viewModel::onNameChange,
                        isError = form.problem != null,
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    )
                }

                FormField(label = stringResource(R.string.expense_type), hint = stringResource(R.string.optional)) {
                    ChoiceChips(
                        options = ExpenseType.entries,
                        selected = form.expenseType,
                        label = { expenseTypeLabel(it) },
                        onSelect = viewModel::onTypeClick
                    )
                }

                FormField(label = stringResource(R.string.category_takes), hint = stringResource(R.string.category_takes_hint)) {
                    MultiChoiceChips(
                        options = BusinessType.TAKEABLE,
                        selected = form.businessTypes,
                        label = { businessTypeLabel(it) },
                        onToggle = viewModel::onBusinessTypeClick
                    )
                }

                HalalaButton(
                    text = stringResource(if (form.id == null) R.string.add else R.string.save),
                    onClick = viewModel::onSaveClick,
                    kind = ButtonKind.Primary,
                    modifier = Modifier.fillMaxWidth()
                )
                if (form.id != null) {
                    HalalaButton(
                        text = stringResource(R.string.category_delete),
                        onClick = viewModel::onDeleteClick,
                        destructive = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
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
