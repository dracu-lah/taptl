package dev.dracu.taptl.pipeline

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.LruCache

/** Memory LRU in front of a SQLite table. Call only from the pipeline worker (SQLite is blocking). */
class TranslationCache(context: Context) {

    // Same file and table the app used before (it was a Room database), so old rows stay.
    private val helper = object : SQLiteOpenHelper(context, "translations.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS translations (zh TEXT NOT NULL PRIMARY KEY, en TEXT NOT NULL)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
    private val memory = LruCache<String, String>(1000)
    private val values = ContentValues(2)

    fun get(zh: String): String? = memory.get(zh) ?: helper.readableDatabase
        .rawQuery("SELECT en FROM translations WHERE zh = ?", arrayOf(zh))
        .use { if (it.moveToFirst()) it.getString(0) else null }
        ?.also { memory.put(zh, it) }

    fun put(zh: String, en: String) {
        memory.put(zh, en)
        values.put("zh", zh)
        values.put("en", en)
        helper.writableDatabase.insertWithOnConflict("translations", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun close() = helper.close()
}
