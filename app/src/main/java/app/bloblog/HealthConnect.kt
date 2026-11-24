package app.bloblog

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
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
            Db(context).markWritten(r.id, true)
        } catch (e: Exception) {
            Log.w("Bloblog", "Health Connect write failed", e)
        }
    }

    /** Deletes the reading's record and marks it unwritten. Returns false if it failed. */
    suspend fun remove(context: Context, readingId: Long): Boolean {
        val client = client(context) ?: return false
        return try {
            client.deleteRecords(WeightRecord::class, emptyList(), listOf("reading-$readingId"))
            Db(context).markWritten(readingId, false)
            true
        } catch (e: Exception) {
            Log.w("Bloblog", "Health Connect delete failed", e)
            false
        }
    }

    suspend fun syncPending(context: Context) {
        Db(context).pendingForMe().forEach { write(context, it) }
    }
}
