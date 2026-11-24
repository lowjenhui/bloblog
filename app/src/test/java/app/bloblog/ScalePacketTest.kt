package app.bloblog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ScalePacketTest {
    // Gradle runs unit tests from the app/ module directory.
    private val capture = File("../docs/scale-capture-2026-11-22.txt").readLines()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { line -> line.trim().split(" ").map { it.toInt(16).toByte() }.toByteArray() }

    @Test fun decodesEveryFinalPacketInCaptureAs6765() {
        val finals = capture.mapNotNull { ScalePacket.parse(it) }.filter { it.isFinal }
        assertEquals(6, finals.size)
        finals.forEach {
            assertEquals(6765, it.weightCentiKg)
            assertEquals("ed673986b4d7", it.scaleId)
        }
    }

    @Test fun measuringPacketsAreNotFinal() {
        val measuring = ScalePacket.parse(capture[3])!!
        assertFalse(measuring.isFinal)
        assertEquals(6765, measuring.weightCentiKg)
    }

    @Test fun rejectsWrongHeaderOrShortData() {
        assertNull(ScalePacket.parse(byteArrayOf(0x12, 0x34) + ByteArray(22)))
        assertNull(ScalePacket.parse(capture[0].copyOf(18)))
    }

    @Test fun captureYieldsExactlyOneReading() {
        var last: Pair<Long, Int>? = null
        var saved = 0
        capture.forEachIndexed { i, bytes ->
            val p = ScalePacket.parse(bytes) ?: return@forEachIndexed
            val now = 1_000_000L + i * 500L // packets ~0.5 s apart
            if (p.shouldSave(last, now)) { saved++; last = now to p.weightCentiKg }
        }
        assertEquals(1, saved)
    }

    @Test fun sameWeightAfterTwoMinutesIsANewReading() {
        val p = ScalePacket.parse(capture.last())!!
        assertFalse(p.shouldSave(0L to 6765, 120_000L))
        assertTrue(p.shouldSave(0L to 6765, 120_001L))
        assertTrue(p.shouldSave(0L to 6700, 10_000L)) // different person/weight
        assertFalse(p.shouldSave(0L to 6760, 10_000L)) // within 0.1 kg
    }
}
