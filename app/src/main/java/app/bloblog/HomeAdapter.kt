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
import java.time.YearMonth
import java.text.SimpleDateFormat
import java.util.Locale

sealed interface HomeItem {
    class Graph(val pts: List<Pt>, val range: Range, val person: Person, val startCenti: Int, val nowMs: Long) : HomeItem
    class HistoryHeader(val count: Int, val month: YearMonth) : HomeItem
    class Months(val months: List<YearMonth>, val selected: YearMonth) : HomeItem
    class Row(val reading: Reading, val notSent: Boolean, val last: Boolean) : HomeItem
    class WhoseRow(val reading: Reading, val last: Boolean) : HomeItem
    object Empty : HomeItem
}

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

/** Fills a view with a theme attribute that is a colour (rounded to [radiusDp]) or a drawable (the gradient). */
fun View.fill(attr: Int, radiusDp: Int = 0, inset: Boolean = false) {
    val tv = TypedValue()
    context.theme.resolveAttribute(attr, tv, true)
    val d = if (tv.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT)
        GradientDrawable().apply { setColor(tv.data); cornerRadius = context.dp(radiusDp).toFloat() }
    else context.getDrawable(tv.resourceId)!!
    // inset: a 48 dp tap target that is drawn 40 dp tall
    background = if (inset) android.graphics.drawable.InsetDrawable(d, 0, context.dp(4), 0, context.dp(4)) else d
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
            is HomeItem.HistoryHeader -> historyHeader(c, item)
            is HomeItem.Months -> months(c, item)
            is HomeItem.Row -> row(c, item.reading, item.last, whose = false, notSent = item.notSent)
            is HomeItem.WhoseRow -> row(c, item.reading, item.last, whose = true, notSent = false).also {
                (box.layoutParams as RecyclerView.LayoutParams).topMargin = -1 // covers the divider above
            }
            HomeItem.Empty -> empty(c)
        })
    }

    private fun text(c: Context, s: CharSequence, sp: Float, colorAttr: Int, bold: Boolean = false) = TextView(c).apply {
        text = s; textSize = sp; setTextColor(c.token(colorAttr)); if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun vertical(c: Context) = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }

    private fun graph(c: Context, g: HomeItem.Graph) = vertical(c).apply {
        setPadding(c.dp(16), 0, c.dp(16), c.dp(32))
        val track = LinearLayout(c).apply { fill(R.attr.bl_surfaceGrey, 20, inset = true) }
        listOf(Range.DAY to "Day", Range.WEEK to "Week", Range.MONTH to "Month", Range.M3 to "3M", Range.YEAR to "Year").forEach { (r, label) ->
            val sel = r == g.range
            track.addView(text(c, label, 12f, if (sel) R.attr.bl_onSelect else R.attr.bl_onGrey).apply {
                gravity = Gravity.CENTER
                if (sel) fill(R.attr.bl_select, 20, inset = true)
                setOnClickListener { onRange(r) }
            }, LinearLayout.LayoutParams(0, c.dp(48), 1f))
        }
        addView(track, LinearLayout.LayoutParams(-1, -2))
        val chart = TrendChartView(c).apply {
            showPerson(g.pts, g.range, g.person.lineAttr(), g.person.dotAttr(), g.nowMs, g.startCenti)
            onScrub = this@HomeAdapter.onScrub
        }
        addView(chart, LinearLayout.LayoutParams(-1, c.dp(220)).apply { topMargin = c.dp(8); marginStart = c.dp(12); marginEnd = c.dp(12) })
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
        setPadding(c.dp(16), 0, c.dp(16), c.dp(8))
        addView(text(c, "History", 16f, R.attr.bl_text, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
        val mon = h.month.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)
        addView(text(c, "${h.count} weigh-in${if (h.count == 1) "" else "s"} in $mon", 12f, R.attr.bl_textMuted))
    }

    private fun months(c: Context, m: HomeItem.Months) = HorizontalScrollView(c).apply {
        isHorizontalScrollBarEnabled = false
        val row = LinearLayout(c).apply { setPadding(c.dp(16), 0, c.dp(16), c.dp(8)) }
        var selected: View? = null
        m.months.zip(monthChipLabels(m.months)).forEach { (month, label) ->
            val sel = month == m.selected
            row.addView(text(c, label, 12f, if (sel) R.attr.bl_onSelect else R.attr.bl_onGrey).apply {
                gravity = Gravity.CENTER
                setPadding(c.dp(16), 0, c.dp(16), 0)
                fill(if (sel) R.attr.bl_select else R.attr.bl_surfaceGrey, 20, inset = true)
                setOnClickListener { onMonth(month) }
                if (sel) selected = this
            }, LinearLayout.LayoutParams(-2, c.dp(48)).apply { marginEnd = c.dp(8) })
        }
        addView(row)
        post { scrollTo((selected?.left ?: 0) - c.dp(16), 0) }
    }

    private fun row(c: Context, r: Reading, last: Boolean, whose: Boolean, notSent: Boolean) = vertical(c).apply {
        setOnClickListener { onRow(r.id) }
        val line = LinearLayout(c).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(c.dp(16), c.dp(12), c.dp(16), c.dp(12))
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
