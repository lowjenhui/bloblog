package app.bloblog

import kotlin.math.abs

/** Spec §7.1. [refs] maps person id to reference weight (centi-kg). Null means unassigned. */
fun assign(weightCentiKg: Int, refs: Map<Long, Int>): Long? {
    val byDistance = refs.entries.sortedBy { abs(it.value - weightCentiKg) }
    val first = byDistance.firstOrNull() ?: return null
    val d1 = abs(first.value - weightCentiKg)
    val d2 = byDistance.getOrNull(1)?.let { abs(it.value - weightCentiKg) } ?: Int.MAX_VALUE
    return if (d1 <= 200 && (d2 == Int.MAX_VALUE || d2 - d1 >= 200)) first.key else null
}
