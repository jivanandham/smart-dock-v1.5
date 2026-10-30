package com.example.data.local

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "dock_notes")
data class DockNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val category: String = "Quick Memo",
    val isPinned: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "dock_agenda_items")
data class DockAgendaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val subtitle: String,
    val providerType: String, // "GMAIL", "OUTLOOK", or "LOCAL"
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val locationOrSender: String = "",
    val isCompleted: Boolean = false
)

@Entity(tableName = "agent_messages")
data class AgentMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String, // "user" or "agent"
    val text: String,
    val sourceBadge: String = "On-Device Core",
    val timestampMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "connected_accounts")
data class ConnectedAccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val emailAddress: String,
    val displayName: String,
    val providerType: String, // "GMAIL" or "OUTLOOK"
    val isSyncEnabled: Boolean = true,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "dock_alarms")
data class DockAlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val label: String,
    val isEnabled: Boolean = true,
    val snoozedUntilMillis: Long? = null,
    val lastTriggeredMinuteKey: Long = 0L,
    /** Bit 0 = Monday … bit 6 = Sunday. 127 = every day, 0 = one-time alarm. */
    @ColumnInfo(defaultValue = "127") val repeatDaysMask: Int = ALL_DAYS_MASK,
    /** Occurrences at or before this time are skipped ("skip next"). */
    val skipUntilMillis: Long? = null,
    @ColumnInfo(defaultValue = "0") val vibrateOnly: Boolean = false,
    /** Require solving a small math problem to stop the alarm. */
    @ColumnInfo(defaultValue = "0") val dismissChallenge: Boolean = false,
    /** Custom alarm sound (content:// URI from the ringtone picker); null = system default. */
    val soundUri: String? = null
) {
    companion object {
        const val ALL_DAYS_MASK = 0x7F
        const val WEEKDAYS_MASK = 0x1F
        const val WEEKEND_MASK = 0x60
    }
}

/** One logged self-care action (water glass, stretch, eye rest, walk, breathing, lunch…). */
@Entity(tableName = "wellness_log")
data class WellnessLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val atMillis: Long,
    /** yyyy-MM-dd in local time, for fast per-day queries. */
    val dayKey: String
)

/** "Top 3 for today" priorities. */
@Entity(tableName = "daily_priorities")
data class DailyPriorityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayKey: String,
    val position: Int,
    val text: String,
    val done: Boolean = false
)

@Dao
interface DockDao {
    // --- Wellness ---
    @Insert
    suspend fun insertWellnessLog(log: WellnessLogEntity): Long

    @Query(
        "DELETE FROM wellness_log WHERE id = (" +
            "SELECT id FROM wellness_log WHERE dayKey = :dayKey AND type IN (:types) " +
            "ORDER BY atMillis DESC, id DESC LIMIT 1" +
            ")"
    )
    suspend fun deleteLatestWellnessLogForTypes(dayKey: String, types: List<String>): Int

    @Query("SELECT * FROM wellness_log WHERE atMillis >= :sinceMillis ORDER BY atMillis ASC")
    fun observeWellnessLogsSince(sinceMillis: Long): Flow<List<WellnessLogEntity>>

    @Query("SELECT * FROM wellness_log WHERE atMillis >= :sinceMillis ORDER BY atMillis ASC")
    suspend fun getWellnessLogsSince(sinceMillis: Long): List<WellnessLogEntity>

    @Query("SELECT * FROM daily_priorities WHERE dayKey = :dayKey ORDER BY position ASC")
    fun observePriorities(dayKey: String): Flow<List<DailyPriorityEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPriority(priority: DailyPriorityEntity): Long

    @Query("UPDATE daily_priorities SET done = :done WHERE id = :id")
    suspend fun setPriorityDone(id: Long, done: Boolean)

    @Query("DELETE FROM daily_priorities WHERE dayKey = :dayKey")
    suspend fun clearPriorities(dayKey: String)

    @Query("SELECT * FROM dock_notes ORDER BY isPinned DESC, createdAtMillis DESC")
    fun observeNotes(): Flow<List<DockNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: DockNoteEntity): Long

    @Query("UPDATE dock_notes SET isPinned = :pinned WHERE id = :id")
    suspend fun setNotePinned(id: Long, pinned: Boolean)

    @Query("DELETE FROM dock_notes WHERE id = :id")
    suspend fun deleteNoteById(id: Long)

    @Query("SELECT * FROM dock_agenda_items ORDER BY isCompleted ASC, startTimeMillis ASC")
    fun observeCustomAgendaItems(): Flow<List<DockAgendaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAgendaItem(item: DockAgendaEntity): Long

    @Query("UPDATE dock_agenda_items SET isCompleted = :completed WHERE id = :id")
    suspend fun setAgendaItemCompleted(id: Long, completed: Boolean)

    @Query("DELETE FROM dock_agenda_items WHERE id = :id")
    suspend fun deleteAgendaItemById(id: Long)

    @Query("DELETE FROM dock_agenda_items WHERE subtitle = :accountEmail")
    suspend fun deleteAgendaItemsByAccount(accountEmail: String)

    @Query("SELECT * FROM agent_messages ORDER BY timestampMillis ASC")
    fun observeAgentMessages(): Flow<List<AgentMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAgentMessage(message: AgentMessageEntity): Long

    @Query("DELETE FROM agent_messages")
    suspend fun clearAgentMessages()

    // Connected Gmail & Outlook Accounts
    @Query("SELECT * FROM connected_accounts ORDER BY createdAtMillis ASC")
    fun observeConnectedAccounts(): Flow<List<ConnectedAccountEntity>>

    @Query("SELECT COUNT(*) FROM connected_accounts")
    suspend fun getConnectedAccountsCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConnectedAccount(account: ConnectedAccountEntity): Long

    @Query("UPDATE connected_accounts SET isSyncEnabled = :enabled WHERE id = :id")
    suspend fun setConnectedAccountSyncEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM connected_accounts WHERE id = :id")
    suspend fun deleteConnectedAccountById(id: Long)

    // Dock Alarms
    @Query("SELECT * FROM dock_alarms ORDER BY hour ASC, minute ASC")
    fun observeAlarms(): Flow<List<DockAlarmEntity>>

    @Query("SELECT * FROM dock_alarms ORDER BY hour ASC, minute ASC")
    suspend fun getAllAlarms(): List<DockAlarmEntity>

    @Query("SELECT COUNT(*) FROM dock_alarms")
    suspend fun getAlarmsCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarm(alarm: DockAlarmEntity): Long

    @Query("UPDATE dock_alarms SET isEnabled = :enabled, snoozedUntilMillis = NULL WHERE id = :id")
    suspend fun setAlarmEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE dock_alarms SET snoozedUntilMillis = :snoozedUntil, lastTriggeredMinuteKey = :minuteKey WHERE id = :id")
    suspend fun updateAlarmSnooze(id: Long, snoozedUntil: Long?, minuteKey: Long)

    @Query(
        "UPDATE dock_alarms SET repeatDaysMask = :repeatDaysMask, skipUntilMillis = :skipUntilMillis, " +
            "vibrateOnly = :vibrateOnly, dismissChallenge = :dismissChallenge, soundUri = :soundUri WHERE id = :id"
    )
    suspend fun updateAlarmOptions(
        id: Long,
        repeatDaysMask: Int,
        skipUntilMillis: Long?,
        vibrateOnly: Boolean,
        dismissChallenge: Boolean,
        soundUri: String?
    )

    @Query("UPDATE dock_alarms SET isEnabled = 0, snoozedUntilMillis = NULL WHERE id = :id")
    suspend fun disableAlarm(id: Long)

    @Query("SELECT * FROM dock_alarms WHERE id = :id")
    suspend fun getAlarm(id: Long): DockAlarmEntity?

    @Query("DELETE FROM dock_alarms WHERE id = :id")
    suspend fun deleteAlarmById(id: Long)
}

@Database(
    entities = [
        DockNoteEntity::class,
        DockAgendaEntity::class,
        AgentMessageEntity::class,
        ConnectedAccountEntity::class,
        DockAlarmEntity::class,
        WellnessLogEntity::class,
        DailyPriorityEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class DockDatabase : RoomDatabase() {
    abstract fun dockDao(): DockDao

    companion object {
        @Volatile
        private var INSTANCE: DockDatabase? = null

        /** Adds the wellness log and daily priorities tables. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `wellness_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`type` TEXT NOT NULL, `atMillis` INTEGER NOT NULL, `dayKey` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `daily_priorities` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`dayKey` TEXT NOT NULL, `position` INTEGER NOT NULL, `text` TEXT NOT NULL, `done` INTEGER NOT NULL)"
                )
            }
        }

        /** Adds repeat days, skip-next, vibrate-only, dismiss challenge and custom sound to alarms. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE dock_alarms ADD COLUMN repeatDaysMask INTEGER NOT NULL DEFAULT 127")
                db.execSQL("ALTER TABLE dock_alarms ADD COLUMN skipUntilMillis INTEGER")
                db.execSQL("ALTER TABLE dock_alarms ADD COLUMN vibrateOnly INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE dock_alarms ADD COLUMN dismissChallenge INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE dock_alarms ADD COLUMN soundUri TEXT")
            }
        }

        fun getInstance(context: Context): DockDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DockDatabase::class.java,
                    "smart_dock.db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

