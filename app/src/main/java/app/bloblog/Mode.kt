package app.bloblog

import android.content.Context

/** Spec §5.8. SOLO hides everyone but the owner. */
enum class PeopleMode { SOLO, DUO }

object ModePrefs {
    private fun prefs(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)
    /** Unset: DUO for an install that already has two people, else SOLO. Saved on first read. */
    fun get(context: Context, db: Db): PeopleMode {
        PeopleMode.values().firstOrNull { it.name == prefs(context).getString("people_mode", null) }?.let { return it }
        return (if (db.people().size == 2) PeopleMode.DUO else PeopleMode.SOLO).also { set(context, it) }
    }
    fun set(context: Context, mode: PeopleMode) = prefs(context).edit().putString("people_mode", mode.name).apply()
}

fun Db.activePeople(mode: PeopleMode): List<Person> = people().filter { mode == PeopleMode.DUO || it.isMe }
