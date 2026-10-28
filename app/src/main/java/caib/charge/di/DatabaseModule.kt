package caib.charge.di

import android.content.Context
import androidx.room.Room
import caib.charge.data.local.CaibDatabase
import caib.charge.data.local.dao.CategoryDao
import caib.charge.data.local.dao.ImportBatchDao
import caib.charge.data.local.dao.LedgerEntryDao
import caib.charge.data.local.dao.SubscriptionDao
import caib.charge.data.time.SystemZoneClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CaibDatabase =
        Room.databaseBuilder(context, CaibDatabase::class.java, CaibDatabase.DATABASE_NAME)
            .addMigrations(CaibDatabase.MIGRATION_1_2, CaibDatabase.MIGRATION_2_3)
            .addCallback(CaibDatabase.SEED_CALLBACK)
            .build()

    @Provides fun provideCategoryDao(database: CaibDatabase): CategoryDao = database.categoryDao()
    @Provides fun provideLedgerEntryDao(database: CaibDatabase): LedgerEntryDao = database.ledgerEntryDao()
    @Provides fun provideImportBatchDao(database: CaibDatabase): ImportBatchDao = database.importBatchDao()
    @Provides fun provideSubscriptionDao(database: CaibDatabase): SubscriptionDao = database.subscriptionDao()

    @Provides
    @Singleton
    fun provideClock(): Clock = SystemZoneClock()
}
