package caib.charge.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import caib.charge.data.local.entity.LedgerEntryEntity
import caib.charge.data.local.entity.LedgerEntryWithCategory
import caib.charge.domain.model.EntryType
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerEntryDao {
    @Query("SELECT COUNT(*) FROM ledger_entries WHERE entry_date_epoch_day BETWEEN :start AND :end")
    suspend fun countRange(start: Long, end: Long): Long

    @Query("SELECT * FROM ledger_entries WHERE entry_date_epoch_day BETWEEN :start AND :end ORDER BY created_at, id")
    suspend fun getRange(start: Long, end: Long): List<LedgerEntryEntity>

    @Query("DELETE FROM ledger_entries WHERE entry_date_epoch_day BETWEEN :start AND :end")
    suspend fun deleteRange(start: Long, end: Long): Int

    @Transaction
    @Query("SELECT * FROM ledger_entries ORDER BY entry_date_epoch_day DESC, created_at DESC, id DESC")
    fun observeAllWithCategories(): Flow<List<LedgerEntryWithCategory>>

    @Transaction
    @Query("SELECT * FROM ledger_entries ORDER BY entry_date_epoch_day ASC, created_at ASC, id ASC")
    suspend fun getAllWithCategories(): List<LedgerEntryWithCategory>

    @Transaction
    @Query("SELECT * FROM ledger_entries WHERE id = :id LIMIT 1")
    suspend fun getWithCategory(id: Long): LedgerEntryWithCategory?

    @Query("SELECT * FROM ledger_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): LedgerEntryEntity?

    @Query("SELECT content_fingerprint FROM ledger_entries WHERE content_fingerprint IN (:fingerprints)")
    suspend fun findExistingFingerprints(fingerprints: List<String>): List<String>

    @Query(
        """SELECT ledger_entries.category_id
            FROM ledger_entries
            INNER JOIN categories ON categories.id = ledger_entries.category_id
            WHERE ledger_entries.type = :type
              AND categories.type = :type
              AND categories.is_archived = 0
            ORDER BY MAX(ledger_entries.created_at, ledger_entries.updated_at) DESC,
                     ledger_entries.updated_at DESC,
                     ledger_entries.created_at DESC,
                     ledger_entries.id DESC
            LIMIT 1"""
    )
    suspend fun getMostRecentActiveCategoryId(type: EntryType): String?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: LedgerEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entries: List<LedgerEntryEntity>): List<Long>

    @Update
    suspend fun update(entry: LedgerEntryEntity): Int

    @Delete
    suspend fun delete(entry: LedgerEntryEntity): Int
}
