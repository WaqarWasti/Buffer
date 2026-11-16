package app.buffer.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Local log of every time a blocked app was opened.
 * An attempt starts as DECLINED (leaving the buffer any way except "continue" counts as declining)
 * and is flipped to CONTINUED if the user holds the continue button.
 */
object Attempts {
    const val DECLINED = "DECLINED"
    const val CONTINUED = "CONTINUED"
    const val DAY_MS = 24L * 60 * 60 * 1000

    private lateinit var helper: DbHelper
    private val _changes = MutableStateFlow(0)

    /** Increments on every write so the UI can refresh. */
    val changes: StateFlow<Int> = _changes

    fun init(context: Context) {
        if (!::helper.isInitialized) helper = DbHelper(context.applicationContext)
    }

    fun record(pkg: String): Long {
        val values = ContentValues().apply {
            put("pkg", pkg)
            put("ts", System.currentTimeMillis())
            put("outcome", DECLINED)
        }
        val id = helper.writableDatabase.insert("attempts", null, values)
        _changes.value++
        return id
    }

    fun markContinued(id: Long) {
        val values = ContentValues().apply { put("outcome", CONTINUED) }
        helper.writableDatabase.update("attempts", values, "id=?", arrayOf(id.toString()))
        _changes.value++
    }

    /** Attempts since [since] (epoch ms), for one package or all when [pkg] is null. */
    fun count(pkg: String?, since: Long): Int =
        scalar("SELECT COUNT(*) FROM attempts WHERE ts>=?" + pkgClause(pkg), args(since.toString(), pkg))

    fun declined(pkg: String?, since: Long, excludeId: Long = -1): Int =
        scalar(
            "SELECT COUNT(*) FROM attempts WHERE outcome='$DECLINED' AND ts>=? AND id!=?" + pkgClause(pkg),
            args(since.toString(), excludeId.toString(), pkg),
        )

    fun lastContinued(pkg: String): Long? =
        helper.readableDatabase.rawQuery(
            "SELECT MAX(ts) FROM attempts WHERE outcome='$CONTINUED' AND pkg=?",
            arrayOf(pkg),
        ).use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null }

    fun countsByPackage(since: Long): Map<String, Int> {
        val out = mutableMapOf<String, Int>()
        helper.readableDatabase.rawQuery(
            "SELECT pkg, COUNT(*) FROM attempts WHERE ts>=? GROUP BY pkg",
            arrayOf(since.toString()),
        ).use { c -> while (c.moveToNext()) out[c.getString(0)] = c.getInt(1) }
        return out
    }

    private fun pkgClause(pkg: String?) = if (pkg != null) " AND pkg=?" else ""

    private fun args(vararg values: String?): Array<String> = values.filterNotNull().toTypedArray()

    private fun scalar(sql: String, args: Array<String>): Int =
        helper.readableDatabase.rawQuery(sql, args).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    private class DbHelper(context: Context) : SQLiteOpenHelper(context, "attempts.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE attempts (id INTEGER PRIMARY KEY AUTOINCREMENT, pkg TEXT NOT NULL, " +
                    "ts INTEGER NOT NULL, outcome TEXT NOT NULL)",
            )
            db.execSQL("CREATE INDEX attempts_pkg_ts ON attempts (pkg, ts)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
}
