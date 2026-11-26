package app.bloblog

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId

object HealthConnect {
    val PERMISSIONS = setOf(HealthPermission.getWritePermission(WeightRecord::class))

    fun client(context: Context): HealthConnectClient? =
        if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE)
            HealthConnectClient.getOrCreate(context) else null

    suspend fun hasPermission(context: Context): Boolean =
        client(context)?.permissionController?.getGrantedPermissions()?.containsAll(PERMISSIONS) == true

    /** Writes [r] and marks it written. On any failure it stays unwritten and [syncPending] retries it. */
    suspend fun write(context: Context, r: Reading) {
        val client = client(context) ?: return
        try {
            val at = Instant.ofEpochMilli(r.takenAt)
            client.insertRecords(listOf(WeightRecord(
                time = at,
                zoneOffset = ZoneId.systemDefault().rules.getOffset(at),
                weight = Mass.kilograms(r.weightCentiKg / 100.0),
                metadata = Metadata.autoRecorded(Device(type = Device.TYPE_SCALE), "reading-${r.id}", 0),
            )))
            Db.get(context).markWritten(r.id, true)
        } catch (e: Exception) {
            Log.w("Bloblog", "Health Connect write failed", e)
        }
    }

    /** Deletes the reading's record and marks it unwritten. Returns false if it failed. */
    suspend fun remove(context: Context, readingId: Long): Boolean {
        val client = client(context)
        if (client == null) {
            // Only "not installed" means nothing is left to remove. "Needs an update" still holds the record,
            // so moving it away (e.g. to the partner) must wait rather than leave it in the owner's Health Connect.
            if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_UNAVAILABLE) return false
            Db.get(context).markWritten(readingId, false)
            return true
        }
        return try {
            client.deleteRecords(WeightRecord::class, emptyList(), listOf("reading-$readingId"))
            Db.get(context).markWritten(readingId, false)
            true
        } catch (e: Exception) {
            Log.w("Bloblog", "Health Connect delete failed", e)
            false
        }
    }

    /** Process-wide: every Health Connect write or remove, with the owner/written checks around it, runs under this lock. */
    val lock = Mutex()

    /** Writes the reading only if it is, right now, the owner's and unsent. The caller holds [lock]. */
    suspend fun writeIfDue(context: Context, hc: HcPort, readingId: Long) {
        val db = Db.get(context)
        val r = db.reading(readingId) ?: return
        if (r.personId != null && r.personId == db.me()?.id && !r.written) hc.write(r)
    }

    suspend fun syncPending(context: Context, hc: HcPort = hcPort(context)) {
        if (!hc.canWrite()) return
        Db.get(context).pendingForMe().forEach { r -> lock.withLock { writeIfDue(context, hc, r.id) } }
    }
}

fun hcPort(context: Context) = object : HcPort {
    override suspend fun remove(readingId: Long) = HealthConnect.remove(context, readingId)
    override suspend fun write(r: Reading) = HealthConnect.write(context, r)
    override suspend fun canWrite() = HealthConnect.hasPermission(context)
}
