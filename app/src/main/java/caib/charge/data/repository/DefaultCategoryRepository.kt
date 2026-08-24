package caib.charge.data.repository

import androidx.room.withTransaction
import caib.charge.data.local.CaibDatabase
import caib.charge.data.local.dao.CategoryDao
import caib.charge.data.local.entity.CategoryEntity
import caib.charge.domain.DomainValidationException
import caib.charge.domain.TextNormalizer
import caib.charge.domain.model.Category
import caib.charge.domain.model.CategoryPresets
import caib.charge.domain.model.EntryType
import caib.charge.domain.repository.CategoryRepository
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class DefaultCategoryRepository @Inject constructor(
    private val database: CaibDatabase,
    private val categoryDao: CategoryDao,
    private val clock: Clock,
) : CategoryRepository {
    override fun observeCategories(type: EntryType?, includeArchived: Boolean): Flow<List<Category>> =
        categoryDao.observe(type, includeArchived).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getCategory(id: String): Category? = categoryDao.getById(id)?.toDomain()

    override suspend fun createCustom(
        type: EntryType,
        name: String,
        iconKey: String,
        colorKey: String,
    ): Category = database.withTransaction {
        val normalizedName = validateFields(name, iconKey, colorKey)
        ensureUniqueName(type, normalizedName)
        val now = clock.millis()
        val entity = CategoryEntity(
            id = UUID.randomUUID().toString(),
            type = type,
            name = name.trim(),
            normalizedName = normalizedName,
            iconKey = iconKey,
            colorKey = colorKey,
            sortOrder = categoryDao.maxActiveSortOrder(type) + 1,
            isBuiltIn = false,
            isArchived = false,
            createdAt = now,
            updatedAt = now,
        )
        categoryDao.insert(entity)
        entity.toDomain()
    }

    override suspend fun updateCustom(id: String, name: String, iconKey: String, colorKey: String): Category =
        database.withTransaction {
            val existing = categoryDao.getById(id) ?: throw DomainValidationException("分类不存在")
            if (existing.isBuiltIn) throw DomainValidationException("内置分类不可修改")
            if (existing.isArchived) throw DomainValidationException("已归档分类不可修改")
            val normalizedName = validateFields(name, iconKey, colorKey)
            ensureUniqueName(existing.type, normalizedName, id)
            val updated = existing.copy(
                name = name.trim(),
                normalizedName = normalizedName,
                iconKey = iconKey,
                colorKey = colorKey,
                updatedAt = clock.millis(),
            )
            requireSingleUpdate(categoryDao.update(updated))
            updated.toDomain()
        }

    override suspend fun reorder(type: EntryType, orderedIds: List<String>) {
        if (orderedIds.distinct().size != orderedIds.size) throw DomainValidationException("分类排序包含重复项")
        database.withTransaction {
            val active = categoryDao.getActiveByType(type)
            if (orderedIds.toSet() != active.map { it.id }.toSet()) {
                throw DomainValidationException("排序必须包含该收支类型的全部可见分类")
            }
            val now = clock.millis()
            orderedIds.forEachIndexed { index, id ->
                requireSingleUpdate(categoryDao.updateSortOrder(id, index, now))
            }
        }
    }

    override suspend fun archive(id: String) {
        database.withTransaction {
            val existing = categoryDao.getById(id) ?: throw DomainValidationException("分类不存在")
            if (existing.isBuiltIn) throw DomainValidationException("内置分类不可归档")
            if (existing.isArchived) return@withTransaction
            val now = clock.millis()
            requireSingleUpdate(categoryDao.update(existing.copy(isArchived = true, updatedAt = now)))
            categoryDao.getActiveByType(existing.type).forEachIndexed { index, category ->
                if (category.sortOrder != index) {
                    requireSingleUpdate(categoryDao.updateSortOrder(category.id, index, now))
                }
            }
        }
    }

    private fun validateFields(name: String, iconKey: String, colorKey: String): String {
        val normalizedName = TextNormalizer.normalize(name)
        if (normalizedName.isEmpty()) throw DomainValidationException("分类名称不能为空")
        val trimmedName = name.trim()
        if (trimmedName.codePointCount(0, trimmedName.length) > 12) {
            throw DomainValidationException("分类名称不能超过 12 字")
        }
        if (iconKey !in CategoryPresets.iconKeys) throw DomainValidationException("分类图标不在预置列表中")
        if (colorKey !in CategoryPresets.colorKeys) throw DomainValidationException("分类颜色不在预置列表中")
        return normalizedName
    }

    private suspend fun ensureUniqueName(type: EntryType, normalizedName: String, excludeId: String = "") {
        if (categoryDao.countNamed(type, normalizedName, excludeId) > 0) {
            throw DomainValidationException("同一收支类型下分类名称不能重复")
        }
    }

    private fun requireSingleUpdate(updatedRows: Int) {
        if (updatedRows != 1) throw DomainValidationException("分类已不存在")
    }
}
