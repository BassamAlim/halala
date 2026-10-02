package bassamalim.halala.features.categories

import bassamalim.halala.core.data.dataSources.room.relations.CategoryWithUse
import bassamalim.halala.core.data.repositories.ClassificationRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

enum class CategoryProblem { NameMissing, NameTaken }

class CategoriesDomain @Inject constructor(
    private val classificationRepository: ClassificationRepository
) {

    fun observeCategories(): Flow<List<CategoryWithUse>> = classificationRepository.observeCategoriesWithUse()

    /**
     * Checks and writes: a new category, or the changes to one (as one change you can undo).
     * [others] are the names of every other category. Null when it was saved.
     */
    suspend fun save(form: CategoryForm, others: List<String>): CategoryProblem? {
        validate(form.name, others)?.let { return it }
        val name = form.name.trim()
        if (form.id == null) {
            val id = classificationRepository.addCategory(name, form.expenseType)
            // What it takes is a change to the others too, so it goes through the same edit.
            if (form.businessTypes.isNotEmpty())
                classificationRepository.editCategory(id, name, form.expenseType, form.businessTypes)
        } else classificationRepository.editCategory(form.id, name, form.expenseType, form.businessTypes)
        return null
    }

    suspend fun delete(id: Long) = classificationRepository.deleteCategory(id)

    companion object {

        /** A category needs a name, and one no other category has, whatever its capitals. */
        fun validate(name: String, others: List<String>): CategoryProblem? = when {
            name.isBlank() -> CategoryProblem.NameMissing
            others.any { it.trim().equals(name.trim(), ignoreCase = true) } -> CategoryProblem.NameTaken
            else -> null
        }
    }
}
