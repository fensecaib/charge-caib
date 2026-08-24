package caib.charge.data.transfer

import androidx.room.withTransaction
import caib.charge.data.local.CaibDatabase
import caib.charge.data.local.dao.CategoryDao
import caib.charge.data.local.dao.ImportBatchDao
import caib.charge.data.local.dao.LedgerEntryDao
import caib.charge.data.local.entity.CategoryEntity
import caib.charge.data.local.entity.ImportBatchEntity
import caib.charge.data.local.entity.LedgerEntryEntity
import caib.charge.data.repository.toDomain
import caib.charge.domain.Fingerprints
import caib.charge.domain.TextNormalizer
import caib.charge.domain.model.BuiltInCategoryIds
import caib.charge.domain.model.CsvExportResult
import caib.charge.domain.model.CsvExportScope
import caib.charge.domain.model.EntrySource
import caib.charge.domain.model.EntryDateLimits
import caib.charge.domain.model.EntryType
import caib.charge.domain.model.ImportCommitResult
import caib.charge.domain.model.ImportPreviewBatch
import caib.charge.domain.model.ImportPreviewRow
import caib.charge.domain.model.ImportRowStatus
import caib.charge.domain.model.MoneyLimits
import caib.charge.domain.repository.CsvTransferService
import caib.charge.di.DefaultDispatcher
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@Singleton
class DefaultCsvTransferService @Inject constructor(
    private val database: CaibDatabase,
    private val categoryDao: CategoryDao,
    private val ledgerDao: LedgerEntryDao,
    private val importBatchDao: ImportBatchDao,
    private val clock: Clock,
    @DefaultDispatcher private val computationDispatcher: CoroutineDispatcher,
) : CsvTransferService {
    override suspend fun previewImport(fileName: String, bytes: ByteArray): ImportPreviewBatch =
        withContext(computationDispatcher) {
            if (bytes.size > CsvTransferService.MAX_IMPORT_BYTES) {
                throw CsvFormatException("CSV 文件不能超过 5 MiB")
            }
            val decoded = CsvCodec.decode(bytes)
            val delimiter = CsvCodec.detectDelimiter(decoded.text)
            val records = CsvCodec.parse(
                text = decoded.text,
                delimiter = delimiter,
                maxRecords = MAX_DATA_ROWS + 1,
                maxFieldsPerRecord = MAX_FIELDS_PER_RECORD,
            )
            if (records.size < 2) throw CsvFormatException("CSV 只有表头，没有可导入的记录")
            val headers = resolveHeaders(records.first())
            val categoryLookup = buildCategoryLookup(categoryDao.getAll())
            val today = LocalDate.now(clock)

            var rows = records.drop(1).mapIndexed { index, values ->
                parsePreviewRow(index + 2, values, headers, categoryLookup, today)
            }
            val existing = findExistingFingerprints(rows.mapNotNull { it.fingerprint })
            val seenInFile = mutableSetOf<String>()
            rows = rows.map { row ->
                val fingerprint = row.fingerprint
                if (row.status == ImportRowStatus.VALID && fingerprint != null &&
                    (fingerprint in existing || !seenInFile.add(fingerprint))
                ) {
                    row.copy(status = ImportRowStatus.DUPLICATE, message = "与已有账目或文件内记录重复")
                } else {
                    if (fingerprint != null) seenInFile += fingerprint
                    row
                }
            }
            ImportPreviewBatch(
                fileName = fileName.substringAfterLast('/').substringAfterLast('\\').ifBlank { "import.csv" }.take(255),
                fileSha256 = Fingerprints.sha256(bytes),
                detectedCharset = decoded.charsetName,
                delimiter = delimiter,
                rows = rows,
            )
        }

    override suspend fun commitImport(
        batch: ImportPreviewBatch,
        selectedRowNumbers: Set<Int>,
        duplicateOverrides: Set<Int>,
    ): ImportCommitResult = database.withTransaction {
        val selected = batch.rows.filter { row ->
            row.rowNumber in selectedRowNumbers &&
                row.status != ImportRowStatus.INVALID &&
                (row.status != ImportRowStatus.DUPLICATE || row.rowNumber in duplicateOverrides)
        }
        if (selected.isEmpty()) throw CsvFormatException("请至少选择一条可导入记录")
        val currentCategories = categoryDao.getAll().associateBy { it.id }
        val today = LocalDate.now(clock)
        val prepared = selected.map { row ->
            row to prepareImportedEntity(row, currentCategories, today)
        }
        val existing = findExistingFingerprints(prepared.map { it.second.contentFingerprint })
        val now = clock.millis()
        val batchId = UUID.randomUUID().toString()
        val seenFingerprints = existing.toMutableSet()
        val accepted = prepared.filter { (row, entity) ->
            val isDuplicate = entity.contentFingerprint in seenFingerprints
            seenFingerprints += entity.contentFingerprint
            !isDuplicate || row.rowNumber in duplicateOverrides
        }.map { (_, entity) ->
            entity.copy(importBatchId = batchId, createdAt = now, updatedAt = now)
        }
        val skippedPreviewDuplicates = batch.rows.count {
            it.status == ImportRowStatus.DUPLICATE &&
                (it.rowNumber !in selectedRowNumbers || it.rowNumber !in duplicateOverrides)
        }
        val skippedDuplicates = skippedPreviewDuplicates + (prepared.size - accepted.size)
        importBatchDao.insert(
            ImportBatchEntity(
                id = batchId,
                fileName = batch.fileName,
                fileSha256 = batch.fileSha256,
                importedAt = now,
                totalRows = batch.totalRows,
                successRows = accepted.size,
                duplicateRows = batch.duplicateRows + (prepared.size - accepted.size),
                invalidRows = batch.invalidRows,
            )
        )
        if (accepted.isNotEmpty()) ledgerDao.insertAll(accepted)
        ImportCommitResult(batchId, accepted.size, skippedDuplicates.coerceAtLeast(0), batch.invalidRows)
    }

    override suspend fun export(scope: CsvExportScope): CsvExportResult = withContext(computationDispatcher) {
        val all = ledgerDao.getAllWithCategories().map { it.toDomain() }
        val targetMonth = (scope as? CsvExportScope.Month)?.let { YearMonth.of(it.year, it.month) }
        val selected = all.filter { entry ->
            when (scope) {
                CsvExportScope.All -> true
                is CsvExportScope.Month -> YearMonth.from(entry.entryDate) == targetMonth
                is CsvExportScope.Year -> entry.entryDate.year == scope.year
            }
        }
        val csv = buildString {
            append('\uFEFF')
            append(CsvCodec.encodeRecord(listOf("日期", "收支类型", "分类", "金额", "备注")))
            append("\r\n")
            selected.forEach { entry ->
                append(
                    CsvCodec.encodeRecord(
                        listOf(
                            entry.entryDate.toString(),
                            entry.type.displayName,
                            CsvValueParser.protectSpreadsheet(entry.category.name),
                            BigDecimal.valueOf(entry.amountCents, 2).toPlainString(),
                            CsvValueParser.protectSpreadsheet(entry.note),
                        ),
                    )
                )
                append("\r\n")
            }
        }
        CsvExportResult(csv.toByteArray(Charsets.UTF_8), selected.size)
    }

    private fun parsePreviewRow(
        rowNumber: Int,
        values: List<String>,
        headers: HeaderIndices,
        categoryLookup: Map<EntryType, CategoryLookup>,
        today: LocalDate,
    ): ImportPreviewRow {
        fun value(index: Int?): String = index?.let { values.getOrNull(it) }.orEmpty().trim()
        val rawDate = value(headers.date)
        val rawType = value(headers.type)
        val rawCategory = CsvValueParser.unprotectSpreadsheet(value(headers.category)).trim()
        val rawAmount = value(headers.amount)
        val note = CsvValueParser.unprotectSpreadsheet(value(headers.note)).trim()
        return try {
            val date = CsvValueParser.parseDate(rawDate)
            if (date.isBefore(EntryDateLimits.MIN_DATE)) throw CsvFormatException("日期不能早于 1900-01-01")
            if (date.isAfter(today)) throw CsvFormatException("日期不能晚于今天")
            val type = CsvValueParser.parseType(rawType)
            val amountCents = CsvValueParser.parseAmount(rawAmount)
            if (note.codePointCount(0, note.length) > 100) throw CsvFormatException("备注超过 100 字")
            val mapping = mapCategory(type, rawCategory, categoryLookup)
            val normalizedNote = TextNormalizer.normalize(note)
            val fingerprint = Fingerprints.entry(date, type, amountCents, mapping.category.id, normalizedNote)
            ImportPreviewRow(
                rowNumber = rowNumber,
                entryDate = date,
                type = type,
                categoryId = mapping.category.id,
                categoryName = mapping.category.name,
                amountCents = amountCents,
                note = note,
                normalizedNote = normalizedNote,
                fingerprint = fingerprint,
                status = ImportRowStatus.VALID,
                mappedToOther = mapping.mappedToOther,
                message = if (mapping.mappedToOther) "原分类“$rawCategory”已映射为“其他”" else null,
            )
        } catch (error: IllegalArgumentException) {
            ImportPreviewRow(
                rowNumber = rowNumber,
                status = ImportRowStatus.INVALID,
                message = error.message ?: "无法解析该行",
            )
        }
    }

    private suspend fun findExistingFingerprints(fingerprints: Iterable<String>): Set<String> {
        val result = mutableSetOf<String>()
        fingerprints.toSet().chunked(SQLITE_IN_QUERY_CHUNK_SIZE).forEach { chunk ->
            result += ledgerDao.findExistingFingerprints(chunk)
        }
        return result
    }

    private fun prepareImportedEntity(
        row: ImportPreviewRow,
        categories: Map<String, CategoryEntity>,
        today: LocalDate,
    ): LedgerEntryEntity {
        val date = row.entryDate ?: throw CsvFormatException("预览状态已变化，请重新预览后再导入")
        val type = row.type ?: throw CsvFormatException("预览状态已变化，请重新预览后再导入")
        val amount = row.amountCents ?: throw CsvFormatException("预览状态已变化，请重新预览后再导入")
        val categoryId = row.categoryId ?: throw CsvFormatException("预览状态已变化，请重新预览后再导入")
        val currentCategory = categories[categoryId]
        if (currentCategory == null || currentCategory.isArchived || currentCategory.type != type ||
            currentCategory.name != row.categoryName
        ) {
            throw CsvFormatException("分类状态已变化，请重新预览后再导入")
        }
        if (date.isBefore(EntryDateLimits.MIN_DATE) || date.isAfter(today)) {
            throw CsvFormatException("日期状态已变化，请重新预览后再导入")
        }
        if (amount !in MoneyLimits.MIN_CENTS..MoneyLimits.MAX_CENTS) {
            throw CsvFormatException("金额状态已变化，请重新预览后再导入")
        }
        if (row.note != row.note.trim() || row.note.codePointCount(0, row.note.length) > 100) {
            throw CsvFormatException("备注状态已变化，请重新预览后再导入")
        }
        val normalizedNote = TextNormalizer.normalize(row.note)
        if (row.normalizedNote != normalizedNote) {
            throw CsvFormatException("备注状态已变化，请重新预览后再导入")
        }
        val fingerprint = Fingerprints.entry(date, type, amount, categoryId, normalizedNote)
        if (row.fingerprint != fingerprint) {
            throw CsvFormatException("账目状态已变化，请重新预览后再导入")
        }
        return LedgerEntryEntity(
            type = type,
            categoryId = categoryId,
            amountCents = amount,
            entryDateEpochDay = date.toEpochDay(),
            note = row.note,
            normalizedNote = normalizedNote,
            source = EntrySource.CSV_IMPORT,
            importBatchId = null,
            contentFingerprint = fingerprint,
            createdAt = 0,
            updatedAt = 0,
        )
    }

    private data class CategoryMapping(val category: CategoryEntity, val mappedToOther: Boolean)

    private fun mapCategory(
        type: EntryType,
        rawName: String,
        categoryLookup: Map<EntryType, CategoryLookup>,
    ): CategoryMapping {
        val normalized = TextNormalizer.normalize(rawName)
        val active = categoryLookup[type] ?: throw CsvFormatException("该收支类型没有可用分类")
        active.byNormalizedName[normalized]?.let { return CategoryMapping(it, false) }
        val aliasId = categoryAliases[type]?.get(normalized)
        aliasId?.let { active.byId[it] }?.let { return CategoryMapping(it, false) }
        val other = active.byId[BuiltInCategoryIds.other(type)]
            ?: throw CsvFormatException("内置“其他”分类不存在")
        return CategoryMapping(other, normalized != other.normalizedName)
    }

    private fun buildCategoryLookup(categories: List<CategoryEntity>): Map<EntryType, CategoryLookup> =
        categories.asSequence()
            .filterNot { it.isArchived }
            .groupBy { it.type }
            .mapValues { (_, values) ->
                CategoryLookup(
                    byNormalizedName = values.associateBy { it.normalizedName },
                    byId = values.associateBy { it.id },
                )
            }

    private fun resolveHeaders(headerRow: List<String>): HeaderIndices {
        val keyed = headerRow.map(CsvValueParser::headerKey)
        fun find(aliases: Set<String>, requiredName: String): Int {
            val matches = keyed.indices.filter { keyed[it] in aliases }
            if (matches.isEmpty()) throw CsvFormatException("缺少“$requiredName”列")
            if (matches.size > 1) throw CsvFormatException("“$requiredName”列重复，无法安全导入")
            return matches.single()
        }
        val noteMatches = keyed.indices.filter { keyed[it] in noteHeaders }
        if (noteMatches.size > 1) throw CsvFormatException("“备注”列重复，无法安全导入")
        return HeaderIndices(
            date = find(dateHeaders, "日期"),
            type = find(typeHeaders, "收支类型"),
            category = find(categoryHeaders, "分类"),
            amount = find(amountHeaders, "金额"),
            note = noteMatches.singleOrNull(),
        )
    }

    private data class HeaderIndices(
        val date: Int,
        val type: Int,
        val category: Int,
        val amount: Int,
        val note: Int?,
    )

    private data class CategoryLookup(
        val byNormalizedName: Map<String, CategoryEntity>,
        val byId: Map<String, CategoryEntity>,
    )

    companion object {
        private val dateHeaders = setOf("日期", "时间", "date", "datetime", "transactiondate", "交易日期", "记账日期")
        private val typeHeaders = setOf("收支类型", "收支", "类型", "type", "transactiontype")
        private val categoryHeaders = setOf("分类", "类别", "category")
        private val amountHeaders = setOf("金额", "amount", "money")
        private val noteHeaders = setOf("备注", "说明", "摘要", "note", "description", "memo")

        private val categoryAliases: Map<EntryType, Map<String, String>> = mapOf(
            EntryType.EXPENSE to aliases(
                BuiltInCategoryIds.EXPENSE_FOOD to listOf("餐饮", "吃饭", "饮食", "food", "meal"),
                BuiltInCategoryIds.EXPENSE_SHOPPING to listOf("购物", "买东西", "网购", "shopping"),
                BuiltInCategoryIds.EXPENSE_MEDICAL to listOf("医疗", "医药", "health", "medical"),
                BuiltInCategoryIds.EXPENSE_DAILY to listOf("日用", "生活用品", "daily"),
                BuiltInCategoryIds.EXPENSE_TRANSPORT to listOf("交通", "出行", "公交", "打车", "transport", "traffic"),
                BuiltInCategoryIds.EXPENSE_PERSONAL to listOf("个人", "personal"),
                BuiltInCategoryIds.EXPENSE_SOCIAL to listOf("社交", "人情", "social"),
                BuiltInCategoryIds.EXPENSE_COMMUNICATION to listOf("通讯", "通信", "话费", "网络", "communication", "phone"),
                BuiltInCategoryIds.EXPENSE_WORK to listOf("工作", "办公", "work"),
                BuiltInCategoryIds.EXPENSE_OTHER to listOf("其他", "其它", "other"),
            ),
            EntryType.INCOME to aliases(
                BuiltInCategoryIds.INCOME_SALARY to listOf("工资", "薪资", "薪水", "salary"),
                BuiltInCategoryIds.INCOME_GIFT to listOf("礼金", "礼物", "gift"),
                BuiltInCategoryIds.INCOME_INVESTMENT to listOf("理财", "投资", "收益", "investment"),
                BuiltInCategoryIds.INCOME_REIMBURSEMENT to listOf("报销", "reimbursement"),
                BuiltInCategoryIds.INCOME_REFUND to listOf("退款", "退货", "refund"),
                BuiltInCategoryIds.INCOME_RED_PACKET to listOf("红包", "redpacket"),
                BuiltInCategoryIds.INCOME_OTHER to listOf("其他", "其它", "other"),
            ),
        )

        private fun aliases(vararg definitions: Pair<String, List<String>>): Map<String, String> =
            definitions.flatMap { (id, values) -> values.map { TextNormalizer.normalize(it) to id } }.toMap()

        private const val MAX_DATA_ROWS = 20_000
        private const val MAX_FIELDS_PER_RECORD = 128
        private const val SQLITE_IN_QUERY_CHUNK_SIZE = 500
    }
}
