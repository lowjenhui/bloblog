package app.bloblog

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrendTest {
    private val day = 86_400_000L

    @Test fun axisCases() {
        assertEquals(Axis(64.5, 66.0, 67.5), axisFor(listOf(65.0, 66.8)))
        assertEquals(Axis(65.0, 66.0, 67.0), axisFor(listOf(65.4, 65.9)))
        assertEquals(Axis(77.0, 78.5, 80.0), axisFor(listOf(77.6, 79.4)))
        assertEquals(Axis(59.5, 65.0, 70.5), axisFor(listOf(60.0, 70.0)))
    }

    @Test fun axisSingleValue() {
        assertEquals(Axis(65.5, 66.0, 66.5), axisFor(listOf(66.0)))
    }

    @Test fun axisWideSpread() {
        val a = axisFor(listOf(65.0, 95.0))
        assertEquals(64.5, a.lo, 1e-9)
        assertTrue(a.hi >= 95.3)
        assertEquals(0.0, (a.mid * 2) % 1.0, 1e-9)
    }

    @Test fun avg7BreaksAfterGap() {
        val segs = avg7Segments(listOf(Pt(0, 6500), Pt(day, 6600), Pt(10 * day, 6700)))
        assertEquals(2, segs.size)
        assertEquals(65.5, segs[0][1].second, 1e-9)
    }

    @Test fun compareBaselineUsesEarlierPoints() {
        val zone = ZoneId.of("UTC")
        val from = LocalDate.of(2026, 10, 10).atStartOfDay(zone).toInstant().toEpochMilli()
        val pts = listOf(Pt(from - 3 * day, 6500), Pt(from + 3_600_000, 6700))
        val s = compareSeries(pts, from, from + 2 * day, zone)
        assertEquals(1.0, s[0].second!!, 1e-9)
    }

    @Test fun compareSeriesUsesLocalDays() {
        val zone = ZoneId.of("Asia/Singapore")
        val at = LocalDate.of(2026, 10, 31).atTime(23, 59).atZone(zone).toInstant().toEpochMilli()
        val from = LocalDate.of(2026, 10, 30).atStartOfDay(zone).toInstant().toEpochMilli()
        val s = compareSeries(listOf(Pt(at, 6500)), from, from + 2 * day, zone)
        assertEquals(listOf(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 1)), s.map { it.first })
        assertEquals(null, s[0].second)
        assertEquals(0.0, s[1].second!!, 1e-9)
    }

    @Test fun summaryWording() {
        assertEquals("Julia is at 65.58 kg, down 0.45 kg.", summaryLine("Julia", 65.58, -0.45, false))
        assertEquals("Julia is at 65.58 kg, up 0.45 kg.", summaryLine("Julia", 65.58, 0.45, false))
        assertEquals("Julia is at 65.58 kg, no change.", summaryLine("Julia", 65.58, 0.04, false))
        assertEquals("Julia was at 65.65 kg, down 0.38 kg.", summaryLine("Julia", 65.65, -0.38, true))
        assertEquals("Sam Lee has no weigh-ins in this range.", summaryLine("Sam Lee", null, null, false))
    }

    @Test fun initialsRule() {
        assertEquals("SL", initials("Sam Lee"))
        assertEquals("JU", initials("Julia"))
        assertEquals("JS", initials("julia stone"))
    }

    @Test fun monthGroupsAcrossYear() {
        val m = listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 12), YearMonth.of(2027, 1))
        assertEquals(listOf(2026 to listOf(m[0], m[1]), 2027 to listOf(m[2])), monthChipGroups(m))
    }
}
