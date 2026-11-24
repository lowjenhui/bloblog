package app.bloblog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssignTest {
    private val owner = mapOf(1L to 6630)
    private val two = mapOf(1L to 6630, 2L to 8000)

    @Test fun onePersonWithin3Kg() {
        assertEquals(1L, assign(6765, owner))
        assertNull(assign(6931, owner))
        assertEquals(1L, assign(6330, owner)) // exactly 3.00 kg away
    }

    @Test fun twoPeopleFarApart() {
        assertEquals(1L, assign(6700, two))
        assertEquals(2L, assign(7900, two))
        assertNull(assign(7300, two))
    }

    @Test fun marginUnder2KgIsUnassigned() {
        assertNull(assign(6690, mapOf(1L to 6630, 2L to 6750)))
    }

    @Test fun nobodyIsUnassigned() {
        assertNull(assign(6700, emptyMap()))
    }
}
