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
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "spam")
data class SpamNumber(
    /** Normalized number used as the match key. */
    @PrimaryKey val number: String,
    val label: String,
    val blocked: Boolean = true,
    val reportedAt: Long = System.currentTimeMillis()
)

@Dao
interface SpamDao {
    @Query("SELECT * FROM spam ORDER BY reportedAt DESC")
    fun flowAll(): Flow<List<SpamNumber>>

    /** Blocking query — safe to call from binder / IO threads. */
    @Query("SELECT * FROM spam WHERE number = :key LIMIT 1")
    fun getByKeySync(key: String): SpamNumber?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: SpamNumber)

    @Query("DELETE FROM spam WHERE number = :key")
    suspend fun delete(key: String)

    @Query("SELECT COUNT(*) FROM spam")
    fun countSync(): Int
}

@Database(entities = [SpamNumber::class], version = 1, exportSchema = false)
abstract class SpamDb : RoomDatabase() {
    abstract fun dao(): SpamDao

    companion object {
        @Volatile
        private var instance: SpamDb? = null

        fun get(context: Context): SpamDb =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SpamDb::class.java,
                    "spam.db"
                ).build().also { instance = it }
            }
    }
}
