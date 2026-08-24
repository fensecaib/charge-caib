package caib.charge.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import caib.charge.data.local.dao.CategoryDao
import caib.charge.data.local.dao.ImportBatchDao
import caib.charge.data.local.dao.LedgerEntryDao
import caib.charge.data.local.entity.CategoryEntity
import caib.charge.data.local.entity.ImportBatchEntity
import caib.charge.data.local.entity.LedgerEntryEntity

@Database(
    entities = [CategoryEntity::class, LedgerEntryEntity::class, ImportBatchEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(CaibTypeConverters::class)
abstract class CaibDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun ledgerEntryDao(): LedgerEntryDao
    abstract fun importBatchDao(): ImportBatchDao

    companion object {
        const val DATABASE_NAME = "caib-charge.db"

        val SEED_CALLBACK = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                BuiltInCategories.entities().forEach { category ->
                    db.execSQL(
                        """INSERT INTO categories
                            (id, type, name, normalized_name, icon_key, color_key, sort_order,
                             is_built_in, is_archived, created_at, updated_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, 1, 0, ?, ?)""".trimIndent(),
                        arrayOf<Any?>(
                            category.id,
                            category.type.name,
                            category.name,
                            category.normalizedName,
                            category.iconKey,
                            category.colorKey,
                            category.sortOrder,
                            category.createdAt,
                            category.updatedAt,
                        ),
                    )
                }
            }
        }
    }
}
