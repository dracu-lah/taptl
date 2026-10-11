package dev.dracu.taptl.pipeline

import android.content.Context
import android.util.LruCache
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "translations")
class TranslationRow(@PrimaryKey val zh: String, val en: String)

@Dao
interface TranslationDao {
    @Query("SELECT en FROM translations WHERE zh = :zh")
    fun get(zh: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun put(row: TranslationRow)
}

@Database(entities = [TranslationRow::class], version = 1)
abstract class CacheDb : RoomDatabase() {
    abstract fun dao(): TranslationDao
}

/** Memory LRU in front of a Room table. Call only from the pipeline worker (Room is blocking). */
class TranslationCache(context: Context) {

    private val memory = LruCache<String, String>(1000)
    private val db = Room.databaseBuilder(context, CacheDb::class.java, "translations.db").build()
    private val dao = db.dao()

    fun get(zh: String): String? =
        memory.get(zh) ?: dao.get(zh)?.also { memory.put(zh, it) }

    fun put(zh: String, en: String) {
        memory.put(zh, en)
        dao.put(TranslationRow(zh, en))
    }

    fun close() = db.close()
}
