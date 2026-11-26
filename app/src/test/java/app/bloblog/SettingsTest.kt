package app.bloblog

import android.content.ContentValues
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun fresh() {
        context.deleteDatabase("bloblog.db")
        ThemePrefs.set(context, AppTheme.COLOURFUL)
    }

    private fun views(v: View): List<View> = listOf(v) + ((v as? ViewGroup)?.let { g -> (0 until g.childCount).flatMap { views(g.getChildAt(it)) } } ?: emptyList())
    private fun text(root: View, t: String) = views(root).firstOrNull { (it as? TextView)?.text?.toString() == t }
    private fun settings() = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
    private fun root(a: SettingsActivity) = a.findViewById<View>(android.R.id.content)

    @Test fun addPersonRunsBackfill() {
        Db(context).addPerson("Julia", 6500, true); ModePrefs.set(context, PeopleMode.DUO)
        Db(context).writableDatabase.insert("reading", null, ContentValues().apply {
            put("taken_at", 1_700_000_000_000L); put("weight_centi", 5500); put("scale_id", "s"); put("raw_hex", "")
        })
        val a = settings()
        text(root(a), "+ Add person")!!.performClick()
        val d = ShadowDialog.getLatestDialog()
        val fields = views(d.window!!.decorView).filterIsInstance<EditText>()
        fields[0].setText("Sam Lee"); fields[1].setText("55")
        d.findViewById<View>(android.R.id.button1).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(2, Db(context).people().size)
        assertNull(Db(context).unassigned().firstOrNull())
        assertEquals("Matched 1 earlier weigh-in to Sam Lee", a.rows.lastMessage)
    }

    @Test fun addHiddenAtTwo() {
        Db(context).addPerson("Julia", 6500, true); ModePrefs.set(context, PeopleMode.DUO)
        assertNotNull(text(root(settings()), "+ Add person"))
        Db(context).addPerson("Sam Lee", 5500, false)
        assertNull(text(root(settings()), "+ Add person"))
    }

    @Test fun themeChoiceSaved() {
        Db(context).addPerson("Julia", 6500, true)
        (text(root(settings()), "Dark")!!.parent.parent as View).performClick()
        assertEquals(AppTheme.DARK, ThemePrefs.get(context))
    }

    @Test fun themeChangeKeepsTabAndRange() {
        Db(context).addPerson("Julia", 6500, true); Db(context).addPerson("Sam Lee", 5500, false)
        val jing = Db(context).people().last().id
        val scenario = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { it.selectTab(jing); it.range = Range.YEAR }
        ThemePrefs.set(context, AppTheme.DARK)
        scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
        scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        scenario.onActivity {
            assertEquals(jing, it.tab)
            assertEquals(Range.YEAR, it.range)
            assertEquals(AppTheme.DARK, it.createdTheme)
        }
    }

    @Test fun peopleNoteSitsAboveAppearance() {
        Db(context).addPerson("Julia", 6500, true)
        val all = views(root(settings()))
        val note = all.indexOfFirst { (it as? TextView)?.text?.startsWith("Weigh-ins far from your weight") == true }
        val appearance = all.indexOfFirst { (it as? TextView)?.text?.toString() == "Appearance" }
        assert(note in 0 until appearance) { "note at $note, Appearance at $appearance" }
    }

    @Test fun dataRowExists() {
        val r = root(settings())
        assertNotNull(text(r, "Data")); assertNotNull(text(r, "Download weigh-ins (CSV)")); assertNotNull(text(r, "Everyone's weigh-ins, one row each."))
    }
}
