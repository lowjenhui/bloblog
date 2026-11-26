package app.bloblog

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LiveUpdateTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun freshDb() {
        Db.resetForTests(); context.deleteDatabase("bloblog.db")
        Db(context).apply { addPerson("Alex", 6500, true); addPerson("Sam Lee", 5500, false) }
    }

    private fun seed(personId: Long?): Long = Db(context).writableDatabase.insert("reading", null, ContentValues().apply {
        put("taken_at", System.currentTimeMillis()); put("weight_centi", 6570); put("scale_id", "s"); put("raw_hex", "")
        put("person_id", personId); put("hc_written", 1)
    })


    private fun send(c: ActivityController<MainActivity>, id: Long) {
        context.sendBroadcast(Intent(ACTION_READING_SAVED).setPackage(context.packageName).putExtra(Notifications.EXTRA_READING_ID, id))
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    @Test fun savedBroadcastRefreshesAndShowsSnackbar() {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup()
        val alex = Db(context).people().first().id
        seed(alex)
        send(c, seed(alex))
        assertEquals("Saved 65.70 kg for Alex", c.get().rows.lastMessage)
    }

    @Test fun unassignedBroadcastNoSnackbar() {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup()
        send(c, seed(null))
        assertNull(c.get().rows.lastMessage)
    }

    @Test fun firstWeighInMessage() {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup()
        send(c, seed(Db(context).people().first().id))
        assertEquals("Your first weigh-in!", c.get().rows.lastMessage)
        send(c, seed(Db(context).people().last().id))
        assertEquals("Sam Lee's first weigh-in!", c.get().rows.lastMessage)
    }

    @Test fun laterWeighInSavedMessage() {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup()
        val alex = Db(context).people().first().id
        seed(alex)
        send(c, seed(alex))
        assertEquals("Saved 65.70 kg for Alex", c.get().rows.lastMessage)
    }
}
