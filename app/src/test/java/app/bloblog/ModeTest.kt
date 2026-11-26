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
class ModeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun fresh() {
        Db.resetForTests(); context.deleteDatabase("bloblog.db")
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun db() = Db(context)
    private fun two() { db().addPerson("Julia", 6500, true); db().addPerson("Sam Lee", 5500, false) }
    private fun views(v: View): List<View> = listOf(v) + ((v as? ViewGroup)?.let { g -> (0 until g.childCount).flatMap { views(g.getChildAt(it)) } } ?: emptyList())
    private fun text(root: View, t: String) = views(root).firstOrNull { (it as? TextView)?.text?.toString() == t && it.visibility == View.VISIBLE }
    private fun settings() = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
    private fun root(a: android.app.Activity) = a.findViewById<View>(android.R.id.content)
    private fun idle() = shadowOf(android.os.Looper.getMainLooper()).idle()
    private fun unassigned(centi: Int) = db().writableDatabase.insert("reading", null, ContentValues().apply {
        put("taken_at", 1_700_000_000_000L); put("weight_centi", centi); put("scale_id", "s"); put("raw_hex", "")
    })

    @Test fun newInstallIsSolo() {
        assertEquals(PeopleMode.SOLO, ModePrefs.get(context, db()))
        db().addPerson("Julia", 6500, true)
        assertEquals(PeopleMode.SOLO, ModePrefs.get(context, db()))
    }

    @Test fun upgradeWithTwoPeopleIsDuo() {
        two()
        assertEquals(PeopleMode.DUO, ModePrefs.get(context, db()))
    }

    @Test fun soloAssignIgnoresHiddenPerson() {
        two()
        val jing = db().people().last().id
        assertEquals(jing, assign(5500, db().refs(0, PeopleMode.DUO)))
        assertNull(assign(5500, db().refs(0, PeopleMode.SOLO)))
        assertEquals(listOf("Julia"), db().activePeople(PeopleMode.SOLO).map { it.name })
    }

    @Test fun soloHidesChipsAndCompare() {
        two(); ModePrefs.set(context, PeopleMode.SOLO)
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertEquals(View.GONE, a.findViewById<View>(R.id.chips).visibility)
        assertEquals(View.VISIBLE, a.findViewById<View>(R.id.headlineSettings).visibility)
        assertNull(text(root(a), "Compare"))
        ModePrefs.set(context, PeopleMode.DUO)
        val b = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertEquals(View.VISIBLE, b.findViewById<View>(R.id.chips).visibility)
        assertEquals(View.GONE, b.findViewById<View>(R.id.headlineSettings).visibility)
    }

    @Test fun soloFallsBackFromHiddenTab() {
        two()
        val me = db().me()!!.id; val jing = db().people().last().id
        val s = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
        for (t in listOf(jing, -1L)) {
            s.onActivity { it.selectTab(t) }
            ModePrefs.set(context, PeopleMode.SOLO)
            s.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
            s.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            s.onActivity { assertEquals(me, it.tab) }
            ModePrefs.set(context, PeopleMode.DUO)
        }
    }

    @Test fun switchToDuoWithoutSecondOpensAdd() {
        db().addPerson("Julia", 6500, true)
        val a = settings()
        text(root(a), "Two people")!!.performClick()
        val d = ShadowDialog.getLatestDialog()
        assertNotNull(text(d.window!!.decorView, "Add person") ?: views(d.window!!.decorView).firstOrNull { it is TextView && it.text == "Add person" })
        d.findViewById<View>(android.R.id.button2).performClick()
        idle()
        assertEquals(PeopleMode.SOLO, ModePrefs.get(context, db()))
    }

    @Test fun switchToDuoRestoresAndBackfills() {
        two(); ModePrefs.set(context, PeopleMode.SOLO)
        val id = unassigned(5500)
        val a = settings()
        assertNull(text(root(a), "Sam Lee"))
        assertNotNull(text(root(a), "Weigh-ins far from your weight are held back and ask “Whose?”, so guests and bags never reach Health Connect."))
        text(root(a), "Two people")!!.performClick()
        idle()
        assertEquals(PeopleMode.DUO, ModePrefs.get(context, db()))
        assertEquals(db().people().last().id, db().reading(id)!!.personId)
        assertNotNull(text(root(a), "Sam Lee"))
        assertNotNull(text(root(a), "Weigh-ins are matched to the closest person. Only the person marked (Me) is sent to Health Connect."))
    }

    @Test fun someoneElseLinkAddsAndSwitches() {
        db().addPerson("Julia", 6500, true)
        val id = unassigned(5500)
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        RowActions(a) {}.openRowSheet(id)
        val sheet = ShadowDialog.getLatestDialog().window!!.decorView
        assertNotNull(text(sheet, "This is me"))
        assertNull(text(sheet, "Move to"))
        text(sheet, "Someone else uses this scale? Add them")!!.performClick()
        val d = ShadowDialog.getLatestDialog()
        val fields = views(d.window!!.decorView).filterIsInstance<EditText>()
        fields[0].setText("Sam Lee"); fields[1].setText("55")
        d.findViewById<View>(android.R.id.button1).performClick()
        idle()
        assertEquals(PeopleMode.DUO, ModePrefs.get(context, db()))
        assertEquals(2, db().people().size)
        assertEquals(db().people().last().id, db().reading(id)!!.personId)
    }

    @Test fun someoneElseLinkRevealsHiddenPerson() {
        two(); ModePrefs.set(context, PeopleMode.SOLO)
        val id = unassigned(5500)
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        RowActions(a) {}.openRowSheet(id)
        text(ShadowDialog.getLatestDialog().window!!.decorView, "Someone else uses this scale? Add them")!!.performClick()
        idle()
        assertEquals(2, db().people().size)
        assertEquals(PeopleMode.DUO, ModePrefs.get(context, db()))
        assertEquals(db().people().last().id, db().reading(id)!!.personId)
    }
}
