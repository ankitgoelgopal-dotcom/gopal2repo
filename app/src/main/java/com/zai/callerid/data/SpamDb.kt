package com.zai.callerid.data

import android.content.Context
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

// ---------------------------------------------------------------- spam

@Entity(tableName = "spam")
data class SpamNumber(
    /** Normalized match key (last 10 digits). */
    @PrimaryKey val number: String,
    val label: String,
    val blocked: Boolean = true,
    val reportedAt: Long = System.currentTimeMillis()
)

// ---------------------------------------------------------------- personal notes (v1.2 → v1.3 with category)

@Entity(tableName = "notes")
data class NumberNote(
    @PrimaryKey val number: String,
    val note: String,
    /** Optional category for per-category ringtone (v1.3). e.g. "Landlord", "Family". */
    val category: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

// ---------------------------------------------------------------- prefix rules (v1.2)

@Entity(tableName = "prefix_rules")
data class PrefixRule(
    /** Numbers starting with this prefix get rejected, e.g. "+92" or "140". */
    @PrimaryKey val prefix: String,
    val label: String = "Prefix block"
)

// ---------------------------------------------------------------- lookup cache (v1.4)

@Entity(tableName = "lookup_cache")
data class LookupCache(
    @PrimaryKey val number: String,
    val country: String,
    val carrier: String,
    val lineType: String,
    val cachedAt: Long = System.currentTimeMillis()
)

// ---------------------------------------------------------------- DAOs

@Dao
interface SpamDao {
    @Query("SELECT * FROM spam ORDER BY reportedAt DESC")
    fun flowAll(): Flow<List<SpamNumber>>

    /** Blocking query — safe to call from binder / IO threads. */
    @Query("SELECT * FROM spam WHERE number = :key LIMIT 1")
    fun getByKeySync(key: String): SpamNumber?

    @Query("SELECT * FROM spam")
    suspend fun once(): List<SpamNumber>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: SpamNumber)

    @Query("DELETE FROM spam WHERE number = :key")
    suspend fun delete(key: String)
}

@Dao
interface NotesDao {
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun flowAll(): Flow<List<NumberNote>>

    @Query("SELECT * FROM notes WHERE number = :key LIMIT 1")
    fun getByKeySync(key: String): NumberNote?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: NumberNote)

    @Query("DELETE FROM notes WHERE number = :key")
    suspend fun delete(key: String)
}

@Dao
interface PrefixDao {
    @Query("SELECT * FROM prefix_rules")
    fun flowAll(): Flow<List<PrefixRule>>

    @Query("SELECT * FROM prefix_rules")
    fun getAllSync(): List<PrefixRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: PrefixRule)

    @Query("DELETE FROM prefix_rules WHERE prefix = :prefix")
    suspend fun delete(prefix: String)
}

@Dao
interface LookupDao {
    @Query("SELECT * FROM lookup_cache WHERE number = :key LIMIT 1")
    fun getByKeySync(key: String): LookupCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: LookupCache)

    @Query("DELETE FROM lookup_cache WHERE number = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM lookup_cache")
    suspend fun clear()
}

// ---------------------------------------------------------------- database

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS notes (" +
                "number TEXT NOT NULL PRIMARY KEY, " +
                "note TEXT NOT NULL, " +
                "updatedAt INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS prefix_rules (" +
                "prefix TEXT NOT NULL PRIMARY KEY, " +
                "label TEXT NOT NULL)"
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notes ADD COLUMN category TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS lookup_cache (" +
                "number TEXT NOT NULL PRIMARY KEY, " +
                "country TEXT NOT NULL, " +
                "carrier TEXT NOT NULL, " +
                "lineType TEXT NOT NULL, " +
                "cachedAt INTEGER NOT NULL)"
        )
    }
}

@Database(
    entities = [SpamNumber::class, NumberNote::class, PrefixRule::class, LookupCache::class],
    version = 4,
    exportSchema = false
)
abstract class SpamDb : RoomDatabase() {
    abstract fun dao(): SpamDao
    abstract fun notesDao(): NotesDao
    abstract fun prefixDao(): PrefixDao
    abstract fun lookupDao(): LookupDao

    companion object {
        @Volatile
        private var instance: SpamDb? = null

        fun get(context: Context): SpamDb =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SpamDb::class.java,
                    "spam.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }
    }
}
