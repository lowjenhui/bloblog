package app.bloblog

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth
import java.time.ZoneId
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35]) // Robolectric 4.18.1 has no SDK 36 jar
class DbTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val packet = ScalePacket("id", 0x23, 6765)
    private val raw = byteArrayOf(1, 2)

    @Before fun freshDb() { context.deleteDatabase("bloblog.db") }

    @Test fun dedupesAcrossInstances() {
        assertTrue(Db(context).saveIfNew(packet, 0, raw) != null)
        assertNull(Db(context).saveIfNew(packet, 30_000, raw))
        assertNull(Db(context).saveIfNew(packet, 60_000, raw))
        assertEquals(1, Db(context).latest().size)
    }

    @Test fun referenceWeights() {
        val db = Db(context)
        db.addPerson("Me", 6630, isMe = true)
        val pid = db.people().single().id
        assertEquals(6630, db.refs(0)[pid])
        val id = db.saveIfNew(ScalePacket("id", 0x23, 6700), 0, raw)!!
        db.setPerson(id, pid)
        assertEquals(6700, db.refs(0)[pid])
    }

    private fun Db.add(w: Int, at: Long, person: Long? = null) {
        val id = saveIfNew(ScalePacket("id", 0x23, w), at, raw)!!
        if (person != null) setPerson(id, person)
    }

    private val day = 86_400_000L

    @Test fun refsUseSevenDayAverage() {
        val db = Db(context)
        db.addPerson("Me", 6000, isMe = true)
        val pid = db.people().single().id
        val now = 20 * day
        db.add(6000, now - 10 * day, pid)
        db.add(6600, now - 3 * day, pid)
        db.add(6700, now - 1 * day, pid)
        assertEquals(6650, db.refs(now)[pid])
    }

    @Test fun luggageHeldBack() {
        val db = Db(context)
        db.addPerson("Me", 6600, isMe = true)
        db.addPerson("Guest", 7840, isMe = false)
        assertNull(assign(8160, db.refs(0)))
    }

    @Test fun backfillOnlyNewPerson() {
        val db = Db(context)
        db.add(7800, 0)
        db.add(6610, day)
        db.addPerson("Me", 6630, isMe = true)
        db.addPerson("Guest", 7840, isMe = false)
        val guest = db.people().last().id
        assertEquals(1, db.backfill(guest))
        assertEquals(listOf(null, guest), db.latest().map { it.personId }) // newest (6610) first
        assertTrue(db.pendingForMe().isEmpty())
    }

    @Test fun pendingForMe() {
        val db = Db(context)
        db.addPerson("Me", 6630, isMe = true)
        val id = db.saveIfNew(packet, 0, raw)!!
        db.setPerson(id, db.me()!!.id)
        assertEquals(listOf(id), db.pendingForMe().map { it.id })
        db.markWritten(id, true)
        assertTrue(db.pendingForMe().isEmpty())
    }

    @Test fun upgradesV1() {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("bloblog.db"), null).apply {
            execSQL("CREATE TABLE reading (id INTEGER PRIMARY KEY, taken_at INTEGER NOT NULL, weight_centi INTEGER NOT NULL, scale_id TEXT NOT NULL, raw_hex TEXT NOT NULL)")
            execSQL("INSERT INTO reading VALUES (1, 1000, 6765, 'id', '00')")
            version = 1
            close()
        }
        val db = Db(context)
        assertEquals(Reading(1, 1000, 6765, null, false), db.latest().single())
        assertTrue(db.pendingForMe().isEmpty())
    }

    @Test fun refsIgnoreLaterReadings() {
        val db = Db(context)
        db.addPerson("Me", 6630, isMe = true)
        val pid = db.people().single().id
        val day = 86_400_000L
        val later = db.saveIfNew(ScalePacket("id", 0x23, 7000), 20 * day, raw)!!
        db.setPerson(later, pid)
        // At day 5 nothing is in the last 7 days and the only assigned reading is from day 20,
        // so the reference falls back to the start weight, not the later reading.
        assertEquals(6630, db.refs(5 * day)[pid])
    }

    @Test fun readingsForRange() {
        val db = Db(context)
        db.addPerson("Me", 6000, isMe = true)
        val pid = db.people().single().id
        db.add(6000, 1000, pid); db.add(6100, 2000, pid); db.add(6200, 3000, pid); db.add(6300, 2500)
        assertEquals(listOf(6000, 6100), db.readingsFor(pid, 1000, 3000).map { it.weightCentiKg })
    }

    @Test fun historyIncludesUnassigned() {
        val db = Db(context)
        db.addPerson("Me", 6000, isMe = true)
        db.addPerson("Guest", 7800, isMe = false)
        val (me, guest) = db.people().map { it.id }
        val sgt = ZoneId.of("Asia/Singapore")
        val t = 1_700_000_000_000L // Nov 2023
        db.add(6000, t, me); db.add(7800, t + 1000, guest); db.add(6100, t + 2000)
        db.add(6200, t + 400 * day, me) // another month
        assertEquals(listOf(6100, 6000), db.historyMonth(me, YearMonth.of(2023, 11), sgt).map { it.weightCentiKg })
        assertEquals(listOf(6100), db.unassigned().map { it.weightCentiKg })
    }

    @Test fun readingCarriesWritten() {
        val db = Db(context)
        val id = db.saveIfNew(packet, 0, raw)!!
        assertEquals(false, db.reading(id)!!.written)
        db.markWritten(id, true)
        assertEquals(true, db.reading(id)!!.written)
    }

    @Test fun monthsUseLocalZone() {
        val db = Db(context)
        db.addPerson("Me", 6000, isMe = true)
        val pid = db.people().single().id
        val sgt = ZoneId.of("Asia/Singapore")
        db.add(6000, java.time.ZonedDateTime.of(2026, 10, 31, 23, 59, 0, 0, sgt).toInstant().toEpochMilli(), pid)
        db.add(6000, java.time.ZonedDateTime.of(2026, 12, 1, 0, 0, 0, 0, sgt).toInstant().toEpochMilli() + 1, pid)
        assertEquals(listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 12)), db.months(pid, sgt))
    }

    @Test fun updatePersonPersists() {
        val db = Db(context)
        db.addPerson("Me", 6000, isMe = true)
        val pid = db.people().single().id
        db.updatePerson(pid, "Rin", 6500)
        assertEquals("Rin", db.people().single().name)
        assertEquals(6500, db.startWeight(pid))
    }

    @Test fun personOrder() {
        val db = Db(context)
        db.addPerson("Me", 6000, isMe = true)
        db.addPerson("Guest", 7800, isMe = false)
        assertEquals(listOf(0, 1), db.people().map { it.order })
    }
}
