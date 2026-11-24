package app.bloblog

import kotlin.math.abs

/** One advertisement from the broadcast-only Yolanda/QN scale. Layout: SPEC.md "Scale protocol". */
data class ScalePacket(val scaleId: String, val status: Int, val weightCentiKg: Int) {
    val isFinal get() = status and 0x20 != 0 && weightCentiKg > 0

    /** Spec rule 2: a final packet counts unless it repeats the last saved weight (±0.1 kg) within 120 s. */
    fun shouldSave(last: Pair<Long, Int>?, nowMs: Long): Boolean {
        if (!isFinal) return false
        if (last == null) return true
        val (lastMs, lastWeight) = last
        return nowMs - lastMs > 120_000 || abs(weightCentiKg - lastWeight) > 10
    }

    companion object {
        const val MANUFACTURER_ID = 0xFFFF

        /** [data] is manufacturer-specific data with the 2-byte company ID already stripped (as Android returns it). */
        fun parse(data: ByteArray?): ScalePacket? {
            if (data == null || data.size < 19) return null
            if (data[0] != 0xAA.toByte() || data[1] != 0xBB.toByte()) return null
            val id = data.copyOfRange(2, 8).joinToString("") { "%02x".format(it) }
            val weight = (data[17].toInt() and 0xFF) or ((data[18].toInt() and 0xFF) shl 8)
            return ScalePacket(id, data[15].toInt() and 0xFF, weight)
        }
    }
}
