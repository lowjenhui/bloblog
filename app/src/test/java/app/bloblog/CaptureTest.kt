package app.bloblog

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private class CountingHc : HcPort {
    var writes = 0
    override suspend fun remove(readingId: Long) = true
    override suspend fun write(r: Reading) { writes++ }
    override suspend fun canWrite() = true
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CaptureTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val hc = CountingHc()

    @Before fun fresh() {
        Db.resetForTests(); context.deleteDatabase("bloblog.db")
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun packet(centi: Int) = ScalePacket("s", 0x20, centi)

    @Test fun ownerWeighInWritesHealthConnectAndBroadcasts() = runBlocking {
        val db = Db(context); db.addPerson("Alex", 6630, true); db.addPerson("Sam Lee", 5500, false)
        ModePrefs.set(context, PeopleMode.DUO)
        val id = handleWeighIn(context, db, packet(6600), byteArrayOf(1), 1_700_000_000_000L, hc)
        assertNotNull(id)
        assertEquals(1, hc.writes)
        assertEquals(1, shadowOf(context as android.app.Application).broadcastIntents.count { it.action == ACTION_READING_SAVED })
    }

    @Test fun soloPartnerWeightIsHeldBack() = runBlocking {
        val db = Db(context); db.addPerson("Alex", 6630, true); db.addPerson("Sam Lee", 7840, false)
        ModePrefs.set(context, PeopleMode.SOLO)
        val id = handleWeighIn(context, db, packet(7820), byteArrayOf(1), 1_700_000_000_000L, hc)!!
        assertEquals(0, hc.writes)
        assertNull(db.reading(id)!!.personId)
    }
}
