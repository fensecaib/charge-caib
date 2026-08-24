package caib.charge.data.repository

import caib.charge.data.local.entity.CategoryEntity
import caib.charge.data.local.entity.LedgerEntryEntity
import caib.charge.data.local.entity.LedgerEntryWithCategory
import caib.charge.domain.model.Category
import caib.charge.domain.model.LedgerEntry
import java.time.LocalDate

internal fun CategoryEntity.toDomain() = Category(
    id = id,
    type = type,
    name = name,
    normalizedName = normalizedName,
    iconKey = iconKey,
    colorKey = colorKey,
    sortOrder = sortOrder,
    isBuiltIn = isBuiltIn,
    isArchived = isArchived,
)

internal fun LedgerEntryWithCategory.toDomain() = LedgerEntry(
    id = entry.id,
    type = entry.type,
    category = category.toDomain(),
    amountCents = entry.amountCents,
    entryDate = LocalDate.ofEpochDay(entry.entryDateEpochDay),
    note = entry.note,
    normalizedNote = entry.normalizedNote,
    source = entry.source,
    importBatchId = entry.importBatchId,
    fingerprint = entry.contentFingerprint,
    createdAt = entry.createdAt,
    updatedAt = entry.updatedAt,
)

internal fun LedgerEntry.toEntity() = LedgerEntryEntity(
    id = id,
    type = type,
    categoryId = category.id,
    amountCents = amountCents,
    entryDateEpochDay = entryDate.toEpochDay(),
    note = note,
    normalizedNote = normalizedNote,
    source = source,
    importBatchId = importBatchId,
    contentFingerprint = fingerprint,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
