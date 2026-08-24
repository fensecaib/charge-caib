package caib.charge.ui.model

import caib.charge.domain.model.Category
import caib.charge.domain.model.EntryType
import caib.charge.domain.model.LedgerEntry

internal fun UiEntryType.toDomain(): EntryType = when (this) {
    UiEntryType.EXPENSE -> EntryType.EXPENSE
    UiEntryType.INCOME -> EntryType.INCOME
}

internal fun EntryType.toUi(): UiEntryType = when (this) {
    EntryType.EXPENSE -> UiEntryType.EXPENSE
    EntryType.INCOME -> UiEntryType.INCOME
}

internal fun Category.toUi() = UiCategory(
    id = id,
    type = type.toUi(),
    name = name,
    iconKey = iconKey,
    colorKey = colorKey.toIntOrNull() ?: 0,
    sortOrder = sortOrder,
    builtIn = isBuiltIn,
    archived = isArchived,
)

internal fun LedgerEntry.toUi() = UiLedgerEntry(
    id = id,
    type = type.toUi(),
    categoryId = category.id,
    categoryName = category.name,
    amountCents = amountCents,
    date = entryDate,
    note = note,
    categoryIconKey = category.iconKey,
    categoryColorKey = category.colorKey.toIntOrNull() ?: 0,
    normalizedNote = normalizedNote,
)
