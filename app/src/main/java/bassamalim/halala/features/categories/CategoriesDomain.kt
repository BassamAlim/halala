package bassamalim.halala.features.categories

import bassamalim.halala.core.data.dataSources.room.relations.CategoryWithUse
import bassamalim.halala.core.data.repositories.ClassificationRepository
import bassamalim.halala.core.enums.ExpenseType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

enum class CategoryProblem { NameMissing, NameTaken }

class CategoriesDomain @Inject constructor(
    private val classificationRepository: ClassificationRepository
) {

    fun observeCategories(): Flow<List<CategoryWithUse>> = classificationRepository.observeCategoriesWithUse()

    /** Checks and writes. Null when it was added. */
    suspend fun add(name: String, type: ExpenseType?, existing: List<String>): CategoryProblem? {
        validate(name, existing)?.let { return it }
        classificationRepository.addCategory(name.trim(), type)
        return null
    }

    suspend fun delete(id: Long) = classificationRepository.deleteCategory(id)

    companion object {

        /** A category needs a name, and one no other category has, whatever its capitals. */
        fun validate(name: String, existing: List<String>): CategoryProblem? = when {
            name.isBlank() -> CategoryProblem.NameMissing
            existing.any { it.trim().equals(name.trim(), ignoreCase = true) } -> CategoryProblem.NameTaken
            else -> null
        }
    }
}
