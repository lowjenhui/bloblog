package app.bloblog

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.text.SimpleDateFormat
import java.util.Locale

sealed interface HomeItem {
    class Graph(val pts: List<Pt>, val range: Range, val person: Person, val startCenti: Int, val nowMs: Long) : HomeItem
    class Compare(val range: Range, val people: List<Person>, val pts: List<List<Pt>>,
                  val series: List<List<Pair<LocalDate, Double?>>>, val fromMs: Long, val zone: ZoneId) : HomeItem
    class HistoryHeader(val count: Int, val month: YearMonth) : HomeItem
    class Months(val months: List<YearMonth>, val selected: YearMonth) : HomeItem
    class Row(val reading: Reading, val notSent: Boolean, val last: Boolean) : HomeItem
    class WhoseRow(val reading: Reading, val last: Boolean) : HomeItem
    object Empty : HomeItem
}

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

/** Fills a view with a theme attribute that is a colour (rounded to [radiusDp]) or a drawable (the gradient). */
// Gaps next to inset controls are 4dp smaller than DESIGN.md says, because the inset already draws 4dp of space.
fun View.fill(attr: Int, radiusDp: Int = 0, inset: Boolean = false) {
    val tv = TypedValue()
    context.theme.resolveAttribute(attr, tv, true)
    val d = if (tv.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT)
        GradientDrawable().apply { setColor(tv.data); cornerRadius = context.dp(radiusDp).toFloat() }
    // Drawable files (Colourful's gradient chip) get the same corners as solid colours.
    else context.getDrawable(tv.resourceId)!!.mutate().also { (it as? GradientDrawable)?.cornerRadius = context.dp(radiusDp).toFloat() }
    // Setting a background replaces the view's padding with the drawable's, so keep the padding the caller set.
    val (l, t, r, b) = listOf(paddingLeft, paddingTop, paddingRight, paddingBottom)
    // inset: a 48 dp tap target that is drawn 40 dp tall
    background = if (inset) android.graphics.drawable.InsetDrawable(d, 0, context.dp(4), 0, context.dp(4)) else d
    setPadding(l, t, r, b)
}

fun Person.lineAttr() = if (order == 0) R.attr.bl_ownerLine else R.attr.bl_secondLine
fun Person.dotAttr() = if (order == 0) R.attr.bl_ownerDot else R.attr.bl_secondDot
fun Person.textAttr() = if (order == 0) R.attr.bl_ownerText else R.attr.bl_secondText

private val rowFmt = SimpleDateFormat("EEE d MMM · HH:mm", Locale.ENGLISH)

class HomeAdapter(
    private val items: List<HomeItem>,
    private val onRange: (Range) -> Unit,
    private val onMonth: (YearMonth) -> Unit,
    private val onRow: (Long) -> Unit,
    private val onScrub: (Int?) -> Unit,
) : RecyclerView.Adapter<HomeAdapter.Holder>() {
    class Holder(v: View) : RecyclerView.ViewHolder(v)

    override fun getItemCount() = items.size
    fun readingIdAt(pos: Int): Long? = when (val i = items.getOrNull(pos)) { is HomeItem.Row -> i.reading.id; is HomeItem.WhoseRow -> i.reading.id; else -> null }
    override fun getItemViewType(position: Int) = items[position].let { if (it is HomeItem.Row || it is HomeItem.WhoseRow) 1 else 0 }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        FrameLayout(parent.context).apply { layoutParams = RecyclerView.LayoutParams(-1, -2) }
    )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val box = holder.itemView as FrameLayout
        val c = box.context
        box.removeAllViews()
        (box.layoutParams as RecyclerView.LayoutParams).topMargin = 0
        box.addView(when (val item = items[position]) {
            is HomeItem.Graph -> graph(c, item)
            is HomeItem.Compare -> compare(c, item)
            is HomeItem.HistoryHeader -> historyHeader(c, item)
            is HomeItem.Months -> months(c, item)
            is HomeItem.Row -> row(c, item.reading, item.last, whose = false, notSent = item.notSent)
            is HomeItem.WhoseRow -> row(c, item.reading, item.last, whose = true, notSent = false).also {
                (box.layoutParams as RecyclerView.LayoutParams).topMargin = -c.dp(1) // covers the divider above
            }
            HomeItem.Empty -> empty(c)
        })
    }

    private fun text(c: Context, s: CharSequence, sp: Float, colorAttr: Int, bold: Boolean = false) = TextView(c).apply {
        text = s; textSize = sp; setTextColor(c.token(colorAttr)); if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun vertical(c: Context) = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }

    private fun pills(c: Context, ranges: List<Pair<Range, String>>, current: Range) = LinearLayout(c).apply {
        fill(R.attr.bl_surfaceGrey, 20, inset = true)
        ranges.forEach { (r, label) ->
            val sel = r == current
            addView(text(c, label, 12f, if (sel) R.attr.bl_onSelect else R.attr.bl_onGrey).apply {
                gravity = Gravity.CENTER
                if (sel) fill(R.attr.bl_select, 20, inset = true)
                setOnClickListener { onRange(r) }
            }, LinearLayout.LayoutParams(0, c.dp(48), 1f))
        }
    }

    private fun compare(c: Context, g: HomeItem.Compare) = vertical(c).apply {
        setPadding(c.dp(16), 0, c.dp(16), c.dp(32))
        val fmt = { f: String -> DateTimeFormatter.ofPattern(f, Locale.ENGLISH) }
        val from = java.time.Instant.ofEpochMilli(g.fromMs).atZone(g.zone)
        addView(text(c, "Change since ${fmt("d MMM").format(from)} · 7-day averages", 12f, R.attr.bl_textMuted).apply { setPadding(0, 0, 0, c.dp(4)) })
        addView(pills(c, listOf(Range.WEEK to "Week", Range.MONTH to "Month", Range.M3 to "3M", Range.YEAR to "Year"), g.range), LinearLayout.LayoutParams(-1, -2))
        val days = g.series[0].map { it.first }
        val summary = text(c, "", 14f, R.attr.bl_text).apply { setPadding(c.dp(12), c.dp(8), c.dp(12), 0) }
        fun show(idx: Int?) {
            val i = (idx ?: days.lastIndex).coerceIn(0, days.lastIndex)
            val sb = SpannableStringBuilder(if (idx == null) "Today" else fmt("EEE d MMM").format(days[i]))
            val endOfDay = days[i].plusDays(1).atStartOfDay(g.zone).toInstant().toEpochMilli() - 1
            g.people.forEachIndexed { k, p ->
                val change = g.series[k][i].second
                val start = sb.length + 1
                sb.append("\n").append(if (change == null && g.pts[k].isNotEmpty()) "${p.name} has no weigh-ins that week."
                    else summaryLine(p.name, change?.let { avg7At(g.pts[k], endOfDay) }, change, past = idx != null))
                sb.setSpan(StyleSpan(Typeface.BOLD), start, start + p.name.length, 0)
                sb.setSpan(ForegroundColorSpan(c.token(p.textAttr())), start, start + p.name.length, 0)
            }
            summary.text = sb
        }
        show(null)
        val chart = TrendChartView(c).apply {
            showCompare(g.people.zip(g.series) { p, s -> p.name to s }, g.people.map { it.lineAttr() })
            onScrub = { show(it); this@HomeAdapter.onScrub(it) }
        }
        addView(chart, LinearLayout.LayoutParams(-1, c.dp(220)).apply { topMargin = c.dp(4); marginStart = c.dp(12); marginEnd = c.dp(12) })
        addView(summary)
    }

    private fun graph(c: Context, g: HomeItem.Graph) = vertical(c).apply {
        setPadding(c.dp(16), 0, c.dp(16), c.dp(32))
        addView(pills(c, listOf(Range.DAY to "Day", Range.WEEK to "Week", Range.MONTH to "Month", Range.M3 to "3M", Range.YEAR to "Year"), g.range), LinearLayout.LayoutParams(-1, -2))
        val chart = TrendChartView(c).apply {
            showPerson(g.pts, g.range, g.person.lineAttr(), g.person.dotAttr(), g.nowMs, g.startCenti)
            onScrub = this@HomeAdapter.onScrub
        }
        val frame = FrameLayout(c).apply { addView(chart, FrameLayout.LayoutParams(-1, -1)) }
        if (!chart.hasPoints) {
            val day = g.range == Range.DAY
            frame.addView(vertical(c).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                addView(ImageView(c).apply {
                    setImageResource(if (day) R.drawable.blob_run else R.drawable.blob_search)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(c.dp(48), c.dp(48)))
                addView(text(c, if (day) "No weigh-in yet today" else "No weigh-ins in this range", 12f, R.attr.bl_textMuted))
            }, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        }
        addView(frame, LinearLayout.LayoutParams(-1, c.dp(220)).apply { topMargin = c.dp(4); marginStart = c.dp(12); marginEnd = c.dp(12) })
        val tail = " · press and slide: the number above follows your finger"
        val legend = if (g.range == Range.DAY) "● weigh-ins$tail" else "● weigh-ins  ━ 7-day average$tail"
        val span = SpannableString(legend).apply {
            setSpan(ForegroundColorSpan(c.token(g.person.dotAttr())), 0, 1, 0)
            val line = legend.indexOf('━')
            if (line >= 0) setSpan(ForegroundColorSpan(c.token(g.person.lineAttr())), line, line + 1, 0)
        }
        addView(text(c, span, 12f, R.attr.bl_textMuted).apply { setPadding(c.dp(12), c.dp(8), c.dp(12), 0) })
    }

    private fun historyHeader(c: Context, h: HomeItem.HistoryHeader) = LinearLayout(c).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(c.dp(16), 0, c.dp(16), c.dp(4))
        addView(text(c, "History", 16f, R.attr.bl_text, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
        val mon = h.month.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)
        addView(text(c, "${h.count} weigh-in${if (h.count == 1) "" else "s"} in $mon", 12f, R.attr.bl_textMuted))
    }

    private fun months(c: Context, m: HomeItem.Months) = HorizontalScrollView(c).apply {
        isHorizontalScrollBarEnabled = false
        val row = LinearLayout(c).apply { setPadding(c.dp(16), 0, c.dp(16), c.dp(4)) }
        var selected: View? = null
        monthChipGroups(m.months).forEachIndexed { i, (year, group) ->
            if (i > 0) row.addView(View(c).apply { setBackgroundColor(c.token(R.attr.bl_divider)) },
                LinearLayout.LayoutParams(c.dp(1), c.dp(24)).apply { gravity = Gravity.CENTER_VERTICAL; marginStart = c.dp(8); marginEnd = c.dp(8) })
            row.addView(text(c, year.toString(), 12f, R.attr.bl_textMuted).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES; contentDescription = year.toString()
            }, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER_VERTICAL; marginStart = c.dp(8); marginEnd = c.dp(8) })
            group.forEach { month ->
                val sel = month == m.selected
                val name = month.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)
                row.addView(text(c, name, 12f, if (sel) R.attr.bl_onSelect else R.attr.bl_onGrey).apply {
                    gravity = Gravity.CENTER
                    setPadding(c.dp(16), 0, c.dp(16), 0)
                    fill(if (sel) R.attr.bl_select else R.attr.bl_surfaceGrey, 20, inset = true)
                    setOnClickListener { onMonth(month) }
                    if (sel) selected = this
                }, LinearLayout.LayoutParams(-2, c.dp(48)).apply { marginEnd = c.dp(8) })
            }
        }
        addView(row)
        post { scrollTo((selected?.left ?: 0) - c.dp(16), 0) }
    }

    private fun row(c: Context, r: Reading, last: Boolean, whose: Boolean, notSent: Boolean) = vertical(c).apply {
        setOnClickListener { onRow(r.id) }
        val line = LinearLayout(c).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(c.dp(16), c.dp(12), c.dp(16), c.dp(12))
            minimumHeight = c.dp(48)
            if (whose) fill(R.attr.bl_notice)
        }
        if (whose) line.addView(ImageView(c).apply { setImageResource(R.drawable.blob_question); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO },
            LinearLayout.LayoutParams(c.dp(24), c.dp(24)).apply { marginEnd = c.dp(8) })
        line.addView(TextView(c).apply {
            textSize = 14f; setTextColor(c.token(R.attr.bl_text))
            val t = rowFmt.format(r.takenAt); val cut = t.indexOf(" · ")
            text = SpannableString(t).apply { setSpan(ForegroundColorSpan(c.token(R.attr.bl_textMuted)), cut, t.length, 0) }
        })
        if (notSent) line.addView(text(c, " · not sent yet", 14f, R.attr.bl_warnText))
        line.addView(View(c), LinearLayout.LayoutParams(0, 0, 1f))
        val kg = "%.2f kg".format(Locale.ROOT, r.weightCentiKg / 100.0)
        line.addView(if (!whose) text(c, kg, 14f, R.attr.bl_text, bold = true) else TextView(c).apply {
            textSize = 14f; setTextColor(c.token(R.attr.bl_text))
            text = SpannableString("$kg · Whose?").apply {
                setSpan(android.text.style.StyleSpan(Typeface.BOLD), 0, kg.length, 0)
                setSpan(ForegroundColorSpan(c.token(R.attr.bl_warnText)), kg.length, length, 0)
            }
        })
        addView(line)
        if (!last) addView(View(c).apply { setBackgroundColor(c.token(R.attr.bl_divider)) },
            LinearLayout.LayoutParams(-1, c.dp(1)).apply { marginStart = c.dp(16); marginEnd = c.dp(16) })
    }

    private fun empty(c: Context) = vertical(c).apply {
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(c.dp(16), c.dp(16), c.dp(16), c.dp(16))
        addView(ImageView(c).apply { setImageResource(R.drawable.blob_peek); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO },
            LinearLayout.LayoutParams(c.dp(96), c.dp(96)))
        addView(text(c, "No weigh-ins yet. Step on the scale.", 14f, R.attr.bl_textMuted).apply { setPadding(0, c.dp(8), 0, 0) })
    }
}
