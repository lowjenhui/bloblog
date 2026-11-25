package app.bloblog

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Reading(val id: Long, val takenAt: Long, val weightCentiKg: Int, val personId: Long?)
data class Person(val id: Long, val name: String, val isMe: Boolean)

class Db(context: Context) : SQLiteOpenHelper(context.applicationContext, "bloblog.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE reading (id INTEGER PRIMARY KEY, taken_at INTEGER NOT NULL, " +
                "weight_centi INTEGER NOT NULL, scale_id TEXT NOT NULL, raw_hex TEXT NOT NULL, " +
                "person_id INTEGER, hc_written INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL(PERSON_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        if (old < 2) {
            db.execSQL(PERSON_TABLE)
            db.execSQL("ALTER TABLE reading ADD COLUMN person_id INTEGER")
            db.execSQL("ALTER TABLE reading ADD COLUMN hc_written INTEGER NOT NULL DEFAULT 0")
        }
    }

    /** Saves [p] if it is a new weigh-in (spec rule 2). Returns the new reading id, or null if not saved. */
    @Synchronized
    fun saveIfNew(p: ScalePacket, nowMs: Long, raw: ByteArray): Long? {
        val last = latest(1).firstOrNull()?.let { it.takenAt to it.weightCentiKg }
        if (!p.shouldSave(last, nowMs)) return null
        return writableDatabase.insert("reading", null, ContentValues().apply {
            put("taken_at", nowMs)
            put("weight_centi", p.weightCentiKg)
            put("scale_id", p.scaleId)
            put("raw_hex", raw.joinToString(" ") { "%02x".format(it) })
        })
    }

    private fun readings(where: String = "", vararg args: Any, limit: Int? = null): List<Reading> =
        readableDatabase.rawQuery(
            "SELECT id, taken_at, weight_centi, person_id FROM reading $where ORDER BY taken_at DESC" +
                if (limit != null) " LIMIT $limit" else "", // limit is an Int, never user text
            args.map { it.toString() }.toTypedArray()
        ).use { c ->
            buildList { while (c.moveToNext()) add(Reading(c.getLong(0), c.getLong(1), c.getInt(2), if (c.isNull(3)) null else c.getLong(3))) }
        }

    fun latest(limit: Int = 500) = readings(limit = limit)

    fun reading(id: Long) = readings("WHERE id = ?", id).firstOrNull()

    fun people(): List<Person> =
        readableDatabase.rawQuery("SELECT id, name, is_me FROM person ORDER BY id", null).use { c ->
            buildList { while (c.moveToNext()) add(Person(c.getLong(0), c.getString(1), c.getInt(2) == 1)) }
        }

    fun me() = people().firstOrNull { it.isMe }

    fun addPerson(name: String, startWeightCenti: Int, isMe: Boolean) {
        writableDatabase.insert("person", null, ContentValues().apply {
            put("name", name)
            put("start_weight_centi", startWeightCenti)
            put("is_me", if (isMe) 1 else 0)
        })
    }

    /** Each person's reference weight at [atMs]: 7-day average, else latest assigned reading, else starting weight (spec §7.1). */
    fun refs(atMs: Long): Map<Long, Int> =
        readableDatabase.rawQuery(
            "SELECT p.id, COALESCE(" +
                "(SELECT CAST(ROUND(AVG(weight_centi)) AS INTEGER) FROM reading r " +
                "WHERE r.person_id = p.id AND r.taken_at > ? AND r.taken_at <= ?), " +
                "(SELECT weight_centi FROM reading r WHERE r.person_id = p.id AND r.taken_at <= ? ORDER BY r.taken_at DESC LIMIT 1), " +
                "p.start_weight_centi) FROM person p",
            arrayOf((atMs - 7 * 86_400_000L).toString(), atMs.toString(), atMs.toString())
        ).use { c -> buildMap { while (c.moveToNext()) put(c.getLong(0), c.getInt(1)) } }

    /** After a person is added: gives them the unassigned readings that now match them (spec §7.3). Returns the count. */
    fun backfill(newPersonId: Long): Int {
        var n = 0
        for (r in readings("WHERE person_id IS NULL").reversed()) {
            if (assign(r.weightCentiKg, refs(r.takenAt)) == newPersonId) { setPerson(r.id, newPersonId); n++ }
        }
        return n
    }

    fun setPerson(readingId: Long, personId: Long?) {
        writableDatabase.execSQL("UPDATE reading SET person_id = ? WHERE id = ?", arrayOf(personId, readingId))
    }

    fun markWritten(readingId: Long, written: Boolean) {
        writableDatabase.execSQL("UPDATE reading SET hc_written = ? WHERE id = ?", arrayOf(if (written) 1 else 0, readingId))
    }

    fun isWritten(readingId: Long): Boolean =
        readableDatabase.rawQuery("SELECT hc_written FROM reading WHERE id = ?", arrayOf(readingId.toString()))
            .use { it.moveToFirst() && it.getInt(0) == 1 }

    fun delete(readingId: Long) {
        writableDatabase.delete("reading", "id = ?", arrayOf(readingId.toString()))
    }

    fun pendingForMe(): List<Reading> {
        val me = me() ?: return emptyList()
        return readings("WHERE person_id = ? AND hc_written = 0", me.id)
    }

    private companion object {
        const val PERSON_TABLE = "CREATE TABLE person (id INTEGER PRIMARY KEY, name TEXT NOT NULL, " +
            "start_weight_centi INTEGER NOT NULL, is_me INTEGER NOT NULL)"
    }
}
