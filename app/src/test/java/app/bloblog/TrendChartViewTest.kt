package app.bloblog

import android.content.Context
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TrendChartViewTest {
    private val ctx: Context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_Bloblog_Colourful)
    private val now = 1_800_000_000_000L
    private val day = 86_400_000L

    private fun view() = TrendChartView(ctx, null).apply {
        measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY))
        layout(0, 0, 1000, 500)
    }

    private fun touch(v: View, action: Int, x: Float) {
        val t = SystemClock.uptimeMillis()
        v.dispatchTouchEvent(MotionEvent.obtain(t, t, action, x, 250f, 0))
    }

    @Test fun contentDescriptionPerson() {
        val pts = listOf(3 to 6500, 2 to 6540, 1 to 6680, 0 to 6620).map { Pt(now - it.first * day, it.second) }
        val v = view()
        v.showPerson(pts, Range.MONTH, R.attr.bl_ownerLine, R.attr.bl_ownerDot, now, 6500)
        assertEquals("Weight trend, month. 4 weigh-ins from 65.0 to 66.8 kg. 7-day average now 65.85 kg.", v.contentDescription)
    }

    @Test fun emptyRangeDescription() {
        val v = view()
        v.showPerson(emptyList(), Range.MONTH, R.attr.bl_ownerLine, R.attr.bl_ownerDot, now, 6500)
        assertEquals("Weight trend, month. No weigh-ins in this range.", v.contentDescription)
    }

    @Test fun scrubReportsNearestIndex() {
        val pts = listOf(6, 5, 3, 1, 0).map { Pt(now - it * day, 6500) }
        val v = view()
        v.showPerson(pts, Range.WEEK, R.attr.bl_ownerLine, R.attr.bl_ownerDot, now, 6500)
        val got = mutableListOf<Int?>()
        v.onScrub = { got.add(it) }
        touch(v, MotionEvent.ACTION_DOWN, 857f)
        touch(v, MotionEvent.ACTION_MOVE, 860f)
        touch(v, MotionEvent.ACTION_UP, 860f)
        assertEquals(listOf<Int?>(3, 3, null), got)
    }

    @Test fun plotStartsAfterLabelGutter() {
        val v = view()
        v.showPerson(listOf(Pt(now - 7 * day, 6500), Pt(now, 6600)), Range.WEEK, R.attr.bl_ownerLine, R.attr.bl_ownerDot, now, 6500)
        val p = android.graphics.Paint().apply { textSize = 12 * ctx.resources.displayMetrics.scaledDensity }
        val widest = p.measureText("67.0 kg")
        assertTrue(v.pointX(0) >= widest)
    }

    @Test fun averageUsesWeekBeforeRangeStart() {
        val v = view()
        v.showPerson(listOf(Pt(now - 8 * day, 6000), Pt(now - 2 * day, 7000)), Range.WEEK, R.attr.bl_ownerLine, R.attr.bl_ownerDot, now, 6500)
        assertEquals("Weight trend, week. 1 weigh-ins from 70.0 to 70.0 kg. 7-day average now 65.00 kg.", v.contentDescription)
    }
}
