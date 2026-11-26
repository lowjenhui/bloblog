package app.bloblog

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

class TrendChartView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var onScrub: ((index: Int?) -> Unit)? = null

    private val dp = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12 * resources.displayMetrics.scaledDensity }

    // person mode
    private var pts = emptyList<Pt>()
    val hasPoints get() = pts.isNotEmpty() // after trimming to the range
    private var range = Range.MONTH
    private var start = 0L
    private var end = 1L
    private var avgByAt = emptyMap<Long, Double>()
    private var segs = emptyList<List<Pair<Long, Double>>>()
    private var lineAttr = 0
    private var dotAttr = 0

    // compare mode
    private var series = emptyList<Pair<String, List<Pair<LocalDate, Double?>>>>()
    private var lineAttrs = emptyList<Int>()
    private var compare = false

    private var axis = Axis(0.0, 0.5, 1.0)
    private var signed = false
    private var xLabels = emptyList<Pair<Float, String>>() // fraction, label
    private var scrub: Int? = null

    fun showPerson(pts: List<Pt>, range: Range, lineAttr: Int, dotAttr: Int, nowMs: Long, emptyCenti: Int) {
        compare = false; signed = false
        this.range = range; this.lineAttr = lineAttr; this.dotAttr = dotAttr
        val zone = ZoneId.systemDefault()
        start = rangeStart(range, nowMs, zone); end = nowMs
        this.pts = pts.filter { it.at in start..end }.sortedBy { it.at }
        // [pts] may include the 7 days before [start], so the first averages are whole weeks
        val all = avg7Segments(pts.filter { it.at <= end })
        segs = if (range == Range.DAY) emptyList() else all.map { s -> s.filter { it.first >= start } }.filter { it.isNotEmpty() }
        avgByAt = all.flatten().toMap()
        axis = axisFor(this.pts.map { it.centi / 100.0 }.ifEmpty { listOf(emptyCenti / 100.0) })
        xLabels = when (range) {
            Range.DAY -> listOf(0f to "00:00", 0.5f to "12:00", 1f to "now")
            Range.YEAR -> (0..4).map { it / 4f to monthName(start + (end - start) * it / 4, zone) }
            else -> listOf(0f, 0.5f, 1f).map { it to dayLabel(start + ((end - start) * it).toLong(), zone) }
        }
        val word = if (range == Range.M3) "3 months" else range.name.lowercase()
        contentDescription = if (this.pts.isEmpty()) "Weight trend, $word. No weigh-ins in this range."
        else {
            val kg = this.pts.map { it.centi / 100.0 }
            val avg = if (range == Range.DAY) "" else String.format(Locale.ROOT, " 7-day average now %.2f kg.", avgByAt.getValue(this.pts.last().at))
            String.format(Locale.ROOT, "Weight trend, %s. %d weigh-ins from %.1f to %.1f kg.", word, kg.size, kg.min(), kg.max()) + avg
        }
        scrub = null; invalidate()
    }

    fun showCompare(series: List<Pair<String, List<Pair<LocalDate, Double?>>>>, lineAttrs: List<Int>) {
        compare = true; signed = true
        this.series = series; this.lineAttrs = lineAttrs
        val days = series.firstOrNull()?.second?.map { it.first }.orEmpty()
        axis = axisFor(series.flatMap { s -> s.second.mapNotNull { it.second } } + 0.0)
        xLabels = if (days.isEmpty()) emptyList() else listOf(0f, 0.5f, 1f).map {
            it to days[((days.size - 1) * it).toInt()].let { d -> "${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}" }
        }
        contentDescription = "Change in weight, " + series.joinToString(", ") { it.first }
        scrub = null; invalidate()
    }

    private fun monthName(ms: Long, z: ZoneId) =
        Instant.ofEpochMilli(ms).atZone(z).month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

    private fun dayLabel(ms: Long, z: ZoneId) =
        Instant.ofEpochMilli(ms).atZone(z).let { "${it.dayOfMonth} ${it.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}" }

    private val top get() = 16 * dp
    private val bottom get() = height - 24 * dp
    private fun yOf(v: Double) = (bottom - (v - axis.lo) / (axis.hi - axis.lo) * (bottom - top)).toFloat()
    private fun yLabels() = listOf(axis.lo, axis.mid, axis.hi).mapIndexed { i, v ->
        val s = if (signed) String.format(Locale.ROOT, "%s%.1f", if (v > 0.0) "+" else if (v < 0.0) "−" else "", abs(v))
        else String.format(Locale.ROOT, "%.1f", v)
        if (i == 2) "$s kg" else s
    }
    internal fun gutter() = yLabels().maxOf { text.measureText(it) } + 8 * dp
    private fun xOfFrac(f: Double) = gutter() + (f * (width - gutter())).toFloat()
    internal fun pointX(i: Int) = xOfTime(pts[i].at)
    private fun xOfTime(t: Long) = xOfFrac((t - start).toDouble() / (end - start))
    private fun days() = series.firstOrNull()?.second?.size ?: 0
    private fun xOfDay(i: Int) = xOfFrac(if (days() < 2) 0.0 else i.toDouble() / (days() - 1))

    override fun onDraw(c: Canvas) {
        val muted = context.token(R.attr.bl_textMuted)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1 * dp
        paint.color = context.token(R.attr.bl_gridline)
        for (v in listOf(axis.lo, axis.mid, axis.hi)) c.drawLine(0f, yOf(v), width.toFloat(), yOf(v), paint)
        text.color = muted; text.textAlign = Paint.Align.LEFT
        listOf(axis.lo, axis.mid, axis.hi).zip(yLabels()).forEach { (v, s) -> c.drawText(s, 0f, yOf(v) - 4 * dp, text) }
        xLabels.forEach { (f, s) ->
            text.textAlign = when (f) { 0f -> Paint.Align.LEFT; 1f -> Paint.Align.RIGHT; else -> Paint.Align.CENTER }
            c.drawText(s, xOfFrac(f.toDouble()), height - 6 * dp, text)
        }
        if (compare) drawCompare(c) else drawPerson(c)
        scrub?.let { i ->
            val x = if (compare) xOfDay(i) else xOfTime(pts[i].at)
            paint.color = context.token(R.attr.bl_text); paint.strokeWidth = 1 * dp
            paint.pathEffect = DashPathEffect(floatArrayOf(4 * dp, 4 * dp), 0f)
            c.drawLine(x, top, x, bottom, paint); paint.pathEffect = null
            paint.strokeWidth = 2 * dp
            if (compare) series.forEachIndexed { s, (_, v) ->
                v[i].second?.let { y -> paint.color = context.token(lineAttrs[s]); c.drawCircle(x, yOf(y), 5 * dp, paint) }
            } else {
                paint.color = context.token(lineAttr)
                c.drawCircle(x, yOf(if (range == Range.DAY) pts[i].centi / 100.0 else avgByAt[pts[i].at] ?: (pts[i].centi / 100.0)), 5 * dp, paint)
            }
        }
    }

    private fun drawPerson(c: Canvas) {
        if (pts.isEmpty()) return // the empty-state sticker and text are views over the chart (HomeAdapter)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.5f * dp; paint.color = context.token(lineAttr)
        segs.forEach { seg ->
            val p = Path()
            seg.forEachIndexed { i, (t, v) -> if (i == 0) p.moveTo(xOfTime(t), yOf(v)) else p.lineTo(xOfTime(t), yOf(v)) }
            c.drawPath(p, paint)
        }
        paint.style = Paint.Style.FILL; paint.color = context.token(dotAttr)
        pts.forEach { c.drawCircle(xOfTime(it.at), yOf(it.centi / 100.0), (if (range == Range.YEAR) 2 else 3) * dp, paint) }
    }

    private fun drawCompare(c: Canvas) {
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1 * dp; paint.color = context.token(R.attr.bl_textMuted)
        paint.pathEffect = DashPathEffect(floatArrayOf(4 * dp, 4 * dp), 0f)
        c.drawLine(gutter(), yOf(0.0), width.toFloat(), yOf(0.0), paint); paint.pathEffect = null
        series.forEachIndexed { s, (name, vals) ->
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.5f * dp; paint.color = context.token(lineAttrs[s])
            var path: Path? = null
            var last: Pair<Float, Float>? = null
            vals.forEachIndexed { i, (_, v) ->
                if (v == null) { path?.let { c.drawPath(it, paint) }; path = null; return@forEachIndexed }
                val x = xOfDay(i); val y = yOf(v)
                if (path == null) path = Path().apply { moveTo(x, y) } else path!!.lineTo(x, y)
                last = x to y
            }
            path?.let { c.drawPath(it, paint) }
            val l = last
            if (l != null && scrub == null) {
                text.textAlign = Paint.Align.RIGHT
                val ty = l.second - 8 * dp
                text.style = Paint.Style.STROKE; text.strokeWidth = 3 * dp; text.color = context.token(R.attr.bl_background)
                c.drawText(name, l.first, ty, text)
                text.style = Paint.Style.FILL; text.color = context.token(lineAttrs[s])
                c.drawText(name, l.first, ty, text)
            }
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val i = if (compare) (0 until days()).minByOrNull { abs(xOfDay(it) - e.x) }
                else pts.indices.minByOrNull { abs(xOfTime(pts[it].at) - e.x) }
                if (i != null) { scrub = i; onScrub?.invoke(i); invalidate() }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                scrub = null; onScrub?.invoke(null); invalidate()
            }
        }
        return true
    }
}
