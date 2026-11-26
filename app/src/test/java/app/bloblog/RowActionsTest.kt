package app.bloblog

import android.content.ContentValues
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakeHc(var removeOk: Boolean = true) : HcPort {
    val calls = mutableListOf<String>()
    val written = mutableListOf<Long>()
    var gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    override suspend fun remove(readingId: Long): Boolean { calls += "remove"; return removeOk }
    override suspend fun write(r: Reading) { gate?.also { gate = null }?.await(); calls += "write"; written += r.id }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RowActionsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val fake = FakeHc()
    private var changed = 0
    private lateinit var actions: RowActions

    @Before fun setUp() {
        context.deleteDatabase("bloblog.db")
        Db(context).apply { addPerson("Julia", 6500, true); addPerson("Sam Lee", 5500, false) }
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        actions = RowActions(a, fake) { changed++ }
    }

    private fun add(personId: Long?, written: Boolean, at: Long = 1_700_000_000_000L): Long = Db(context).writableDatabase.insert("reading", null, ContentValues().apply {
        put("taken_at", at); put("weight_centi", 6570); put("scale_id", "s"); put("raw_hex", "")
        put("person_id", personId); put("hc_written", if (written) 1 else 0)
    })

    private val julia get() = Db(context).people().first { it.isMe }.id
    private val jing get() = Db(context).people().first { !it.isMe }.id

    @Test fun moveAwayFromOwnerRemovesFirst() = runBlocking {
        val id = add(julia, true)
        assertTrue(actions.moveReading(id, jing))
        assertEquals(listOf("remove"), fake.calls)
        assertEquals(jing, Db(context).reading(id)!!.personId)
        assertEquals(1, changed)
    }

    @Test fun moveToOwnerWrites() = runBlocking {
        val id = add(null, false)
        assertTrue(actions.moveReading(id, julia))
        assertEquals(listOf("write"), fake.calls)
    }

    @Test fun removeFailureKeepsRow() = runBlocking {
        val id = add(julia, true)
        fake.removeOk = false
        assertFalse(actions.moveReading(id, jing))
        assertEquals(julia, Db(context).reading(id)!!.personId)
        assertEquals(0, changed)
    }

    @Test fun deleteUnwrittenSkipsHc() = runBlocking {
        val id = add(jing, false)
        assertTrue(actions.deleteReading(id))
        assertTrue(fake.calls.isEmpty())
        assertNull(Db(context).reading(id))
    }

    @Test fun deleteWrittenRemovesFirst() = runBlocking {
        val id = add(julia, true)
        assertTrue(actions.deleteReading(id))
        assertEquals(listOf("remove"), fake.calls)
        assertNull(Db(context).reading(id))
    }

    @Test fun actingOnDeletedReadingIsNoOp() = runBlocking {
        assertFalse(actions.moveReading(999, jing))
        assertFalse(actions.deleteReading(999))
        assertTrue(fake.calls.isEmpty())
        assertEquals(0, changed)
    }

    @Test fun sheetAndDialogOpen() {
        val id = add(julia, true)
        actions.openRowSheet(id)
        actions.confirmDelete(id)
        actions.showSaved("Julia", 6570)
        actions.showMatched(6, "Sam Lee")
        assertNotNull(org.robolectric.shadows.ShadowDialog.getLatestDialog())
    }

    @Test fun deleteDialogHasPeek() {
        val id = add(julia, true)
        actions.confirmDelete(id)
        val root = org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView
        fun walk(v: android.view.View, f: (android.view.View) -> Unit) { f(v); if (v is android.view.ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i), f) }
        val ids = mutableListOf<Int>(); val texts = mutableListOf<String>()
        walk(root) { if (it is android.widget.ImageView && it.drawable != null) ids += org.robolectric.Shadows.shadowOf(it.drawable).createdFromResId; if (it is android.widget.TextView) texts += it.text.toString() }
        assertTrue(ids.contains(R.drawable.blob_peek))
        assertTrue(texts.contains("Delete this weigh-in?"))
    }

    @Test fun readingMovedAwayIsNotWrittenBySyncPending() = runBlocking {
        val a = add(julia, false, 1_700_000_002_000L); val b = add(julia, false, 1_700_000_001_000L)
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>(); fake.gate = gate
        val sync = launch { HealthConnect.syncPending(context, fake) }
        yield() // sync is now inside the write of the newest reading
        val move = launch { actions.moveReading(b, jing) }
        yield()
        gate.complete(Unit)
        sync.join(); move.join()
        assertEquals(listOf(a), fake.written)
        assertEquals(jing, Db(context).reading(b)!!.personId)
    }
}
