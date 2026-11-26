package app.bloblog

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Every weigh-in, oldest first, as RFC 4180 CSV (hidden person and unassigned included). */
fun weighInsCsv(db: Db, zone: ZoneId): String {
    val names = db.people().associate { it.id to it.name }
    val date = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(zone)
    val time = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)
    fun q(s: String) = if (s.any { it in ",\"\r\n" }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    return "date,time,weight_kg,person,in_health_connect\n" + db.latest(Int.MAX_VALUE).reversed().joinToString("") {
        val at = Instant.ofEpochMilli(it.takenAt)
        listOf(date.format(at), time.format(at), "%.2f".format(Locale.ROOT, it.weightCentiKg / 100.0),
            q(names[it.personId].orEmpty()), if (it.written) "yes" else "no").joinToString(",") + "\n"
    }
}
