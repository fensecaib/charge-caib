package caib.charge.data.repository

import androidx.room.withTransaction
import caib.charge.data.local.BuiltInCategories
import caib.charge.data.local.CaibDatabase
import caib.charge.data.local.entity.CategoryEntity
import caib.charge.data.local.entity.LedgerEntryEntity
import caib.charge.data.transfer.MigrationCodec
import caib.charge.domain.Fingerprints
import caib.charge.domain.TextNormalizer
import caib.charge.domain.model.*
import caib.charge.domain.repository.MigrationTransferService
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class MigrationRepository @Inject constructor(private val db: CaibDatabase, private val clock: Clock) : MigrationTransferService {
    override suspend fun export(start: LocalDate, end: LocalDate): Pair<String, Int> = withContext(Dispatchers.IO) {
        MigrationCodec.validate(MigrationData(start.toEpochDay(), end.toEpochDay(), emptyList(), emptyList()), LocalDate.now(clock))
        val data = db.withTransaction {
            val rows = boundedRange(start.toEpochDay(), end.toEpochDay())
            val usedCategoryIds = rows.mapTo(HashSet()) { it.categoryId }
            val cats = db.categoryDao().getAll().filter { it.id in usedCategoryIds }
            val indices = cats.withIndex().associate { it.value.id to it.index }
            MigrationData(start.toEpochDay(), end.toEpochDay(), cats.map {
                MigrationCategory(if (it.isBuiltIn) it.id else "", it.type, it.name, it.iconKey, it.colorKey, it.isArchived)
            }, rows.map { MigrationEntry(indices.getValue(it.categoryId), it.amountCents, it.entryDateEpochDay, it.note, it.createdAt, it.updatedAt) })
        }
        MigrationCodec.encode(data, LocalDate.now(clock)) to data.entries.size
    }

    override suspend fun preview(code: String): MigrationPreview = withContext(Dispatchers.IO) {
        val data = MigrationCodec.decode(code, LocalDate.now(clock))
        validateBuiltIns(data)
        db.withTransaction {
            val rows = boundedRange(data.start, data.end)
            MigrationPreview(data, MigrationSnapshot.token(rows, db.categoryDao().getAll()), rows.size)
        }
    }

    override suspend fun commit(preview: MigrationPreview): Int = withContext(Dispatchers.IO) {
        MigrationCodec.validate(preview.data, LocalDate.now(clock))
        validateBuiltIns(preview.data)
        db.withTransaction {
            val data = preview.data
            val cats = db.categoryDao().getAll()
            val rows = boundedRange(data.start, data.end)
            require(MigrationSnapshot.token(rows, cats) == preview.localToken) { "本机数据已变化，请重新预览后覆盖" }
            val byName = cats.associateBy { it.type to it.normalizedName }.toMutableMap()
            val nextOrders = EntryType.entries.associateWith { type ->
                (cats.filter { it.type == type }.maxOfOrNull { it.sortOrder } ?: -1).toLong() + 1
            }.toMutableMap()
            val mapped = data.categories.map { incoming ->
                val normalized = TextNormalizer.normalize(incoming.name)
                byName[incoming.type to normalized] ?: run {
                    val now = clock.millis()
                    val order = nextOrders.getValue(incoming.type)
                    require(order <= Int.MAX_VALUE) { "分类排序超出范围" }
                    CategoryEntity(UUID.randomUUID().toString(), incoming.type, incoming.name, normalized,
                        incoming.icon, incoming.color, order.toInt(),
                        false, incoming.archived, now, now).also {
                        db.categoryDao().insert(it)
                        byName[incoming.type to normalized] = it
                        nextOrders[incoming.type] = order + 1
                    }
                }
            }
            db.ledgerEntryDao().deleteRange(data.start, data.end)
            db.ledgerEntryDao().insertAll(data.entries.map {
                val category = mapped[it.category]
                val normalizedNote = TextNormalizer.normalize(it.note)
                LedgerEntryEntity(type = category.type, categoryId = category.id, amountCents = it.amount,
                    entryDateEpochDay = it.day, note = it.note, normalizedNote = normalizedNote,
                    source = EntrySource.MANUAL, importBatchId = null,
                    contentFingerprint = Fingerprints.entry(LocalDate.ofEpochDay(it.day), category.type, it.amount, category.id, normalizedNote),
                    createdAt = it.createdAt, updatedAt = it.updatedAt)
            })
            data.entries.size
        }
    }

    private fun validateBuiltIns(data: MigrationData) {
        val presets = BuiltInCategories.entities()
        data.categories.filter { it.builtInId.isNotEmpty() }.forEach { incoming ->
            require(presets.any { it.id == incoming.builtInId && it.type == incoming.type && it.name == incoming.name &&
                it.iconKey == incoming.icon && it.colorKey == incoming.color && !incoming.archived }) { "内置分类配置无效" }
        }
    }

    /** Always called inside the same transaction as subsequent reads/writes. */
    private suspend fun boundedRange(start: Long, end: Long): List<LedgerEntryEntity> {
        require(db.ledgerEntryDao().countRange(start, end) <= MigrationLimits.MAX_ROWS) {
            "所选范围超过 5 万笔，请缩小日期范围"
        }
        return db.ledgerEntryDao().getRange(start, end)
    }
}
