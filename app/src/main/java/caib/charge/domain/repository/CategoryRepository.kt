package caib.charge.domain.repository

import caib.charge.domain.model.Category
import caib.charge.domain.model.EntryType
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun observeCategories(type: EntryType? = null, includeArchived: Boolean = false): Flow<List<Category>>
    suspend fun getCategory(id: String): Category?
    suspend fun createCustom(type: EntryType, name: String, iconKey: String, colorKey: String): Category
    suspend fun updateCustom(id: String, name: String, iconKey: String, colorKey: String): Category
    suspend fun reorder(type: EntryType, orderedIds: List<String>)
    suspend fun archive(id: String)
}
