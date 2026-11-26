package app.bloblog

import android.Manifest
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

const val ACTION_READING_SAVED = "app.bloblog.READING_SAVED"

object Scanner {
    enum class State { LISTENING, NO_PERMISSION, BLUETOOTH_OFF, FAILED }

    /** Registers a system-held scan that wakes [ScanReceiver] even when the app is closed. Safe to call repeatedly. */
    fun start(context: Context): State {
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
            return State.NO_PERMISSION
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        val scanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner ?: return State.BLUETOOTH_OFF
        // Filter on the AA BB header so the system only wakes us for QN/Yolanda scale broadcasts.
        val filter = ScanFilter.Builder()
            .setManufacturerData(ScalePacket.MANUFACTURER_ID, byteArrayOf(0xAA.toByte(), 0xBB.toByte()), byteArrayOf(-1, -1))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()
        val code = scanner.startScan(listOf(filter), settings, pendingIntent(context))
        return if (code == 0) State.LISTENING else State.FAILED.also { Log.w("Bloblog", "startScan failed: $code") }
    }

    private fun pendingIntent(context: Context) = PendingIntent.getBroadcast(
        context, 0, Intent(context, ScanReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE, // mutable: the system adds the scan results
    )
}

class ScanReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val error = intent.getIntExtra(BluetoothLeScanner.EXTRA_ERROR_CODE, 0)
        if (error != 0) { Log.w("Bloblog", "scan error $error"); return }
        // The typed overload needs API 33 and minSdk is 31.
        @Suppress("DEPRECATION")
        val results = intent.getParcelableArrayListExtra<ScanResult>(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT) ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = Db.get(context)
                for (r in results) {
                    val raw = r.scanRecord?.getManufacturerSpecificData(ScalePacket.MANUFACTURER_ID) ?: continue
                    val p = ScalePacket.parse(raw) ?: continue
                    handleWeighIn(context, db, p, raw, System.currentTimeMillis())
                }
            } catch (e: Exception) {
                Log.e("Bloblog", "capture failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Saves, assigns and (for the owner) sends one weigh-in. Returns the reading id, or null if it was not saved. */
suspend fun handleWeighIn(context: Context, db: Db, p: ScalePacket, raw: ByteArray, nowMs: Long, hc: HcPort = hcPort(context)): Long? {
    val id = db.saveIfNew(p, nowMs, raw) ?: return null
    Log.i("Bloblog", "saved ${p.weightCentiKg / 100.0} kg")
    val personId = assign(p.weightCentiKg, db.refs(nowMs, ModePrefs.get(context, db)))
    db.setPerson(id, personId)
    val reading = db.reading(id) ?: return null
    if (personId == null) Notifications.unassigned(context, reading)
    else HealthConnect.lock.withLock { HealthConnect.writeIfDue(context, hc, id) }
    context.sendBroadcast(Intent(ACTION_READING_SAVED).setPackage(context.packageName).putExtra(Notifications.EXTRA_READING_ID, id))
    return id
}

/** System-held scans don't survive a reboot or an app update, so re-register them. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Any app can send an explicit intent, and repeated scan starts get throttled.
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        Scanner.start(context)
    }
}
