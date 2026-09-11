package caib.charge.di

import caib.charge.data.repository.DefaultCategoryRepository
import caib.charge.data.repository.DefaultLedgerRepository
import caib.charge.data.transfer.DefaultCsvTransferService
import caib.charge.domain.repository.CategoryRepository
import caib.charge.domain.repository.CsvTransferService
import caib.charge.domain.repository.LedgerRepository
import caib.charge.domain.repository.MigrationTransferService
import caib.charge.data.repository.MigrationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindMigrationService(implementation: MigrationRepository): MigrationTransferService

    @Binds
    @Singleton
    abstract fun bindLedgerRepository(implementation: DefaultLedgerRepository): LedgerRepository

    @Binds
    @Singleton
    abstract fun bindCategoryRepository(implementation: DefaultCategoryRepository): CategoryRepository

    @Binds
    @Singleton
    abstract fun bindCsvTransferService(implementation: DefaultCsvTransferService): CsvTransferService

}
