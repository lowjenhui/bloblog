package app.bloblog

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

data class Pt(val at: Long, val centi: Int)

enum class Range { DAY, WEEK, MONTH, M3, YEAR }

data class Axis(val lo: Double, val mid: Double, val hi: Double)

private const val DAY_MS = 86_400_000L
private const val WEEK_MS = 7 * DAY_MS

fun rangeStart(r: Range, nowMs: Long, zone: ZoneId): Long = when (r) {
    Range.DAY -> Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
    Range.WEEK -> nowMs - 7 * DAY_MS
    Range.MONTH -> nowMs - 30 * DAY_MS
    Range.M3 -> nowMs - 90 * DAY_MS
    Range.YEAR -> nowMs - 365 * DAY_MS
}

/** Mean kg of points in (t - 7d, t], or null if none. */
internal fun avg7At(pts: List<Pt>, t: Long): Double? =
    pts.filter { it.at > t - WEEK_MS && it.at <= t }.map { it.centi / 100.0 }.average().takeIf { !it.isNaN() }

fun avg7Segments(pts: List<Pt>): List<List<Pair<Long, Double>>> {
    val sorted = pts.sortedBy { it.at }
    val out = mutableListOf<MutableList<Pair<Long, Double>>>()
    sorted.forEachIndexed { i, p ->
        if (i == 0 || p.at - sorted[i - 1].at > WEEK_MS) out.add(mutableListOf())
        out.last().add(p.at to avg7At(sorted, p.at)!!)
    }
    return out
}

fun axisFor(values: List<Double>, pad: Double = 0.3): Axis {
    val lo = floor((values.min() - pad) * 2) / 2
    val step = max(0.5, ceil(((values.max() + pad) - lo) / 2 * 2) / 2)
    return Axis(lo, lo + step, lo + 2 * step)
}

fun compareSeries(pts: List<Pt>, fromMs: Long, toMs: Long, zone: ZoneId): List<Pair<LocalDate, Double?>> {
    val first = Instant.ofEpochMilli(fromMs).atZone(zone).toLocalDate()
    val last = Instant.ofEpochMilli(toMs).atZone(zone).toLocalDate()
    val days = generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList()
    val avgs = days.map { d -> avg7At(pts, d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1) }
    val base = avg7At(pts, fromMs) ?: avgs.firstOrNull { it != null }
    return days.zip(avgs) { d, a -> d to if (a == null || base == null) null else a - base }
}

fun summaryLine(name: String, kg: Double?, changeKg: Double?, past: Boolean): String {
    if (kg == null) return "$name has no weigh-ins in this range."
    val change = if (changeKg == null || abs(changeKg) < 0.05) "no change"
    else String.format(Locale.ROOT, "%s %.2f kg", if (changeKg < 0) "down" else "up", abs(changeKg))
    return String.format(Locale.ROOT, "%s %s %.2f kg, %s.", name, if (past) "was at" else "is at", kg, change)
}

fun initials(name: String): String {
    val words = name.trim().split(Regex("\\s+"))
    val s = if (words.size > 1) "${words[0].first()}${words[1].first()}" else words[0].take(2)
    return s.uppercase(Locale.ROOT)
}

fun monthChipLabels(months: List<YearMonth>): List<String> = months.mapIndexed { i, m ->
    val name = m.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)
    if (i == 0 || m.monthValue == 1) "$name ${m.year}" else name
}
