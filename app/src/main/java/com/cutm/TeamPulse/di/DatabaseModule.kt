package com.cutm.TeamPulse.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cutm.TeamPulse.data.local.TeamPulseDatabase
import com.cutm.TeamPulse.data.local.dao.ProjectDao
import com.cutm.TeamPulse.data.local.dao.StudentDao
import com.cutm.TeamPulse.data.local.dao.StudentProgressDao
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

    /**
     * Migration from version 1 to version 2: Add student_progress table for gamification
     */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS student_progress (
                    studentEmail TEXT NOT NULL PRIMARY KEY,
                    totalXp INTEGER NOT NULL DEFAULT 0,
                    tasksCompleted INTEGER NOT NULL DEFAULT 0,
                    hasTaskMasterBadge INTEGER NOT NULL DEFAULT 0,
                    lastModifiedLocal INTEGER NOT NULL,
                    localDirty INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY(studentEmail) REFERENCES students(studentEmail) ON DELETE CASCADE
                )
            """)
            db.execSQL("""
                CREATE INDEX IF NOT EXISTS index_student_progress_studentEmail 
                ON student_progress(studentEmail)
            """)
        }
    }

    /**
     * Migration from version 2 to version 3: Add hasEverBeenCompleted to task_assignments
     * (One-time XP guard: prevents re-award on repeated complete-revert-complete cycles)
     */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                ALTER TABLE task_assignments 
                ADD COLUMN hasEverBeenCompleted INTEGER NOT NULL DEFAULT 0
            """)
        }
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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .fallbackToDestructiveMigration(dropAllTables = true)  // Only if migration fails
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
    fun provideStudentProgressDao(database: TeamPulseDatabase): StudentProgressDao =
        database.studentProgressDao()

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
