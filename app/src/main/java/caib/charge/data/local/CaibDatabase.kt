package caib.charge.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import caib.charge.data.local.dao.CategoryDao
import caib.charge.data.local.dao.ImportBatchDao
import caib.charge.data.local.dao.LedgerEntryDao
import caib.charge.data.local.dao.SubscriptionDao
import caib.charge.data.local.entity.CategoryEntity
import caib.charge.data.local.entity.ImportBatchEntity
import caib.charge.data.local.entity.LedgerEntryEntity
import caib.charge.data.local.entity.SubscriptionPeriodEntity
import caib.charge.data.local.entity.SubscriptionProjectEntity

@Database(
    entities = [CategoryEntity::class, LedgerEntryEntity::class, ImportBatchEntity::class,
        SubscriptionProjectEntity::class, SubscriptionPeriodEntity::class],
    version = 3,
    exportSchema = true,
)
@TypeConverters(CaibTypeConverters::class)
abstract class CaibDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun ledgerEntryDao(): LedgerEntryDao
    abstract fun importBatchDao(): ImportBatchDao
    abstract fun subscriptionDao(): SubscriptionDao

    companion object {
        const val DATABASE_NAME = "caib-charge.db"

        /** Additive only: ledger, category and import-batch rows and indices are untouched. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `subscription_projects` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `note` TEXT NOT NULL, `is_stopped` INTEGER NOT NULL, `revision` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)""")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `subscription_periods` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `project_id` INTEGER NOT NULL, `sort_order` INTEGER NOT NULL, `start_epoch_day` INTEGER NOT NULL, `end_epoch_day` INTEGER NOT NULL, `duration_value` INTEGER, `duration_unit` TEXT, `amount_cents` INTEGER, `note` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`project_id`) REFERENCES `subscription_projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_subscription_periods_project_id_sort_order` ON `subscription_periods` (`project_id`, `sort_order`)")
            }
        }

        /** Appearance keys are additive metadata; all existing records keep their identity. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `subscription_projects` ADD COLUMN `icon_key` TEXT NOT NULL DEFAULT 'auto'")
                db.execSQL("ALTER TABLE `subscription_projects` ADD COLUMN `color_key` INTEGER NOT NULL DEFAULT 0")
            }
        }

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
