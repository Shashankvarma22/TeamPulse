package com.cutm.TeamPulse.di

import android.content.Context
import androidx.room.Room
import com.cutm.TeamPulse.data.local.TeamPulseDatabase
import com.cutm.TeamPulse.data.local.dao.ProjectDao
import com.cutm.TeamPulse.data.local.dao.StudentDao
import com.cutm.TeamPulse.data.local.dao.SyncMetadataDao
import com.cutm.TeamPulse.data.local.dao.SyncQueueDao
import com.cutm.TeamPulse.data.local.dao.TaskAssignmentDao
import com.cutm.TeamPulse.data.local.dao.TeamDao
import com.cutm.TeamPulse.data.local.dao.UserSessionDao
import com.cutm.TeamPulse.core.security.SqlCipherKeyProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    init {
        System.loadLibrary("sqlcipher")
    }

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        sqlCipherKeyProvider: SqlCipherKeyProvider,
    ): TeamPulseDatabase {
        val factory = SupportOpenHelperFactory(sqlCipherKeyProvider.getPassphrase())
        return Room.databaseBuilder(
            context,
            TeamPulseDatabase::class.java,
            DATABASE_NAME,
        )
            .openHelperFactory(factory)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    @Provides
    fun provideUserSessionDao(database: TeamPulseDatabase): UserSessionDao =
        database.userSessionDao()

    @Provides
    fun provideProjectDao(database: TeamPulseDatabase): ProjectDao =
        database.projectDao()

    @Provides
    fun provideTeamDao(database: TeamPulseDatabase): TeamDao =
        database.teamDao()

    @Provides
    fun provideStudentDao(database: TeamPulseDatabase): StudentDao =
        database.studentDao()

    @Provides
    fun provideTaskAssignmentDao(database: TeamPulseDatabase): TaskAssignmentDao =
        database.taskAssignmentDao()

    @Provides
    fun provideSyncQueueDao(database: TeamPulseDatabase): SyncQueueDao =
        database.syncQueueDao()

    @Provides
    fun provideSyncMetadataDao(database: TeamPulseDatabase): SyncMetadataDao =
        database.syncMetadataDao()

    private const val DATABASE_NAME = "teampulse.db"
}
