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
        assertEquals(6630, db.refs()[pid])
        val id = db.saveIfNew(ScalePacket("id", 0x23, 6700), 0, raw)!!
        db.setPerson(id, pid)
        assertEquals(6700, db.refs()[pid])
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
        assertEquals(Reading(1, 1000, 6765, null), db.latest().single())
        assertTrue(db.pendingForMe().isEmpty())
    }
}
