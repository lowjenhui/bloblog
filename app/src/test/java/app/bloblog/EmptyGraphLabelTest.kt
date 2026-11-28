package app.bloblog

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real text measuring (NATIVE graphics), so wrapping shows up like on a phone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EmptyGraphLabelTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before fun fresh() { context.deleteDatabase("bloblog.db"); Db.resetForTests(); Db.get(context).addPerson("Alex", 6500, true) }

    private fun views(v: View): List<View> = listOf(v) + ((v as? ViewGroup)?.let { g -> (0 until g.childCount).flatMap { views(g.getChildAt(it)) } } ?: emptyList())

    private fun label(range: Range, text: String): TextView {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        a.range = range; a.selectTab(Db.get(context).me()!!.id)
        val root = a.window.decorView
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY)); root.layout(0, 0, 1080, 2340)
        return views(a.findViewById<RecyclerView>(R.id.list)).filterIsInstance<TextView>().first { it.text.toString() == text }
    }

    @Test fun dayLabelFitsOnOneLine() = assertEquals(1, label(Range.DAY, "No weigh-in yet today").lineCount)

    @Test fun rangeLabelFitsOnOneLine() = assertEquals(1, label(Range.MONTH, "No weigh-ins in this range").lineCount)
}
