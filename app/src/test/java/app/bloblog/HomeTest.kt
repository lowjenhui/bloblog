package app.bloblog

import android.content.ContentValues
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val now = System.currentTimeMillis()
    private val hour = 3_600_000L
    private val day = 24 * hour

    @Before fun freshDb() { context.deleteDatabase("bloblog.db") }

    private fun db() = Db(context).also { if (it.people().isEmpty()) { it.addPerson("Julia", 6500, true); it.addPerson("Sam Lee", 5500, false) } }

    private fun add(personId: Long?, at: Long, centi: Int, written: Boolean = true) {
        db().writableDatabase.insert("reading", null, ContentValues().apply {
            put("taken_at", at); put("weight_centi", centi); put("scale_id", "s"); put("raw_hex", "")
            put("person_id", personId); put("hc_written", if (written) 1 else 0)
        })
    }

    private fun home(): MainActivity {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        layOut(a)
        return a
    }

    private fun layOut(a: MainActivity) {
        val list = a.findViewById<RecyclerView>(R.id.list)
        list.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(20000, View.MeasureSpec.EXACTLY))
        list.layout(0, 0, 1080, 20000)
    }

    private fun texts(v: View, out: MutableList<TextView> = mutableListOf()): List<TextView> {
        if (v is TextView && v.visibility == View.VISIBLE) out.add(v)
        if (v is ViewGroup) for (i in 0 until v.childCount) texts(v.getChildAt(i), out)
        return out
    }

    private fun listTexts(a: MainActivity) = texts(a.findViewById(R.id.list)).map { it.text.toString() }

    @Test fun historyNewestFirst() {
        val p = db().me()!!.id
        add(p, now - 2 * hour, 6500); add(p, now - hour, 6510); add(p, now - 3 * hour, 6490)
        val t = listTexts(home()).filter { it.endsWith(" kg") }
        assertEquals(listOf("65.10 kg", "65.00 kg", "64.90 kg"), t)
    }

    @Test fun whoseRowShownOnEveryTab() {
        val d = db(); val second = d.people()[1]
        add(d.me()!!.id, now - hour, 6500); add(null, now - 2 * hour, 8120)
        val a = home()
        assertTrue(listTexts(a).contains("81.20 kg · Whose?"))
        a.selectTab(second.id); layOut(a)
        assertTrue(listTexts(a).contains("81.20 kg · Whose?"))
    }

    @Test fun rangeDefaultsToMonth() = assertEquals(Range.MONTH, home().range)

    @Test fun emptyPersonShowsPlaceholder() {
        db()
        assertTrue(listTexts(home()).contains("No weigh-ins yet. Step on the scale."))
    }

    @Test fun headlineTextToday() {
        val p = db().me()!!.id
        add(p, now - 1000, 6570); add(p, now - 2000 - day, 6546)
        val a = home()
        assertEquals("65.70 kg", a.findViewById<TextView>(R.id.headlineWeight).text.toString())
        assertEquals("today · 7-day avg 65.58", a.findViewById<TextView>(R.id.headlineLine2).text.toString())
    }

    @Test fun headlineTextPastDay() {
        val p = db().me()!!.id
        val at = now - day - 1000
        add(p, at, 6570); add(p, at - 2 * day, 6546)
        val label = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH).format(java.time.Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()))
        assertEquals("$label · 7-day avg 65.58", home().findViewById<TextView>(R.id.headlineLine2).text.toString())
    }

    @Test fun notSentMarkerOnlyOwner() {
        val d = db(); val second = d.people()[1]
        add(d.me()!!.id, now - hour, 6500, written = false)
        add(second.id, now - 2 * hour, 5500, written = false)
        val a = home()
        val marker = texts(a.findViewById(R.id.list)).filter { it.text.toString().contains("not sent yet") }
        assertEquals(1, marker.size)
        assertEquals(a.token(R.attr.bl_warnText), marker[0].currentTextColor)
        a.selectTab(second.id); layOut(a)
        assertFalse(listTexts(a).any { it.contains("not sent yet") })
    }

    @Test fun historyCountExcludesUnassigned() {
        val p = db().me()!!.id
        add(p, now - hour, 6500); add(null, now - 2 * hour, 8120)
        val month = java.time.Instant.ofEpochMilli(now - hour).atZone(ZoneId.systemDefault()).month
            .getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)
        // both land in the same month unless the test runs in the first 2 hours of one; the count is what matters
        assertTrue(listTexts(home()).any { it == "1 weigh-in in $month" })
    }

    @Test fun tapTargetsAtLeast48dp() {
        val p = db().me()!!.id
        add(p, now - hour, 6500)
        val a = home()
        val min = 48 * a.resources.displayMetrics.density
        val chip = a.findViewById<ViewGroup>(R.id.chips).getChildAt(0)
        val button = a.findViewById<View>(R.id.stripButton)
        val pill = texts(a.findViewById(R.id.list)).first { it.text == "Week" }
        val monthChip = texts(a.findViewById(R.id.list)).first { it.text.toString().matches(Regex("[A-Z][a-z]{2} \\d{4}")) }
        assertTrue(chip.layoutParams.height >= min)
        assertTrue(button.layoutParams.height >= min)
        assertTrue(pill.height >= min)
        assertTrue(monthChip.height >= min)
    }
}
