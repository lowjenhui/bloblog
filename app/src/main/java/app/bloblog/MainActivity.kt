package app.bloblog

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Readings screen (SPEC screen 4). */
class MainActivity : ComponentActivity() {
    private lateinit var status: TextView
    private lateinit var allow: Button
    private lateinit var hcBanner: TextView
    private lateinit var hcConnect: Button
    private lateinit var empty: TextView
    private lateinit var list: ListView
    private val fmt = SimpleDateFormat("EEE d MMM · HH:mm", Locale.getDefault())
    private var shown = emptyList<Reading>()
    private var setupShowing = false
    private var askedNotifications = false

    private val hcPermission = registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { refresh() }
    private val bluetoothPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        scanState = Scanner.start(this); refresh()
    }
    // Android throttles >5 scan starts per 30 s and a throttled scan silently stops background capture,
    // so start the scan only on resume and on permission grant, never from refresh().
    private var scanState = Scanner.State.NO_PERMISSION
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Readings"
        val pad = (16 * resources.displayMetrics.density).toInt()
        status = TextView(this).apply { setPadding(pad, pad, pad, pad / 2) }
        allow = Button(this).apply {
            text = "Allow Bluetooth"
            setOnClickListener { bluetoothPermission.launch(Manifest.permission.BLUETOOTH_SCAN) }
        }
        hcBanner = TextView(this).apply { setPadding(pad, pad / 2, pad, pad / 2) }
        hcConnect = Button(this).apply {
            text = "Connect"
            setOnClickListener { hcPermission.launch(HealthConnect.PERMISSIONS) }
        }
        empty = TextView(this).apply { text = "No weigh-ins yet. Step on the scale."; setPadding(pad, pad, pad, pad) }
        list = ListView(this).apply { setOnItemClickListener { _, _, i, _ -> choose(shown[i].id) } }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            addView(status); addView(allow); addView(hcBanner); addView(hcConnect); addView(empty); addView(list)
        })
        if (savedInstanceState == null) openRequested(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openRequested(intent)
    }

    /** Opened from the "Whose weigh-in?" notification. */
    private fun openRequested(intent: Intent) {
        val id = intent.getLongExtra(Notifications.EXTRA_READING_ID, -1)
        if (id >= 0) choose(id)
    }

    // shortcut: list refreshes only when the screen is opened or resumed, add live updates if that gets annoying.
    override fun onResume() {
        super.onResume()
        scanState = Scanner.start(this)
        refresh()
        lifecycleScope.launch { HealthConnect.syncPending(this@MainActivity); refresh() }
    }

    private fun refresh() {
        val db = Db(this)
        if (db.me() == null) showSetup(db)
        val state = scanState
        status.text = when (state) {
            Scanner.State.LISTENING -> "Listening for your scale, even when Blob-log is closed."
            Scanner.State.NO_PERMISSION -> "Blob-log listens for your scale in the background."
            Scanner.State.BLUETOOTH_OFF -> "Bluetooth is off. Turn it on, then reopen Blob-log."
            Scanner.State.FAILED -> "Couldn't start listening. Reopen Blob-log to try again."
        }
        allow.visibility = if (state == Scanner.State.NO_PERMISSION) View.VISIBLE else View.GONE
        if (Build.VERSION.SDK_INT >= 33 && state == Scanner.State.LISTENING && !askedNotifications &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askedNotifications = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        lifecycleScope.launch {
            val available = HealthConnect.client(this@MainActivity) != null
            val connected = available && HealthConnect.hasPermission(this@MainActivity)
            hcBanner.visibility = if (connected) View.GONE else View.VISIBLE
            hcBanner.text = if (available) "Health Connect isn't connected. Readings are saved here but not sent."
            else "Health Connect isn't available on this phone."
            hcConnect.visibility = if (available && !connected) View.VISIBLE else View.GONE
        }
        shown = db.latest()
        val names = db.people().associate { it.id to it.name }
        val rows = shown.map {
            "${fmt.format(Date(it.takenAt))}    %.2f kg    ${names[it.personId] ?: "Who's this?"}".format(it.weightCentiKg / 100.0)
        }
        empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, rows)
    }

    private fun showSetup(db: Db) {
        if (setupShowing) return
        setupShowing = true
        val name = EditText(this).apply { hint = "Name" }
        val weight = EditText(this).apply {
            hint = "Current weight (kg)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, 0)
            addView(name); addView(weight)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Who uses the scale?")
            .setView(form)
            .setPositiveButton("Next", null) // real handler below, so invalid input keeps the dialog open
            .setCancelable(false)
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val kg = weight.text.toString().replace(',', '.').toDoubleOrNull()
            when {
                name.text.isBlank() -> name.error = "Enter a name."
                kg == null || kg < 20 || kg > 250 -> weight.error = "Enter a weight between 20 and 250 kg."
                else -> {
                    db.addPerson(name.text.toString().trim(), Math.round(kg * 100).toInt(), isMe = true)
                    setupShowing = false
                    dialog.dismiss()
                    refresh()
                }
            }
        }
    }

    private fun choose(readingId: Long) {
        val people = Db(this).people()
        val items = people.map { it.name } + "Not us — delete"
        AlertDialog.Builder(this)
            .setTitle("Whose weigh-in?")
            .setItems(items.toTypedArray()) { _, i ->
                lifecycleScope.launch { if (i < people.size) assignTo(readingId, people[i]) else delete(readingId) }
            }
            .show()
    }

    private suspend fun assignTo(readingId: Long, person: Person) {
        val db = Db(this)
        val wasWritten = db.isWritten(readingId)
        if (wasWritten && !person.isMe && !HealthConnect.remove(this, readingId)) return hcFailed()
        db.setPerson(readingId, person.id)
        if (person.isMe && !wasWritten) db.reading(readingId)?.let { HealthConnect.write(this, it) }
        done(readingId)
    }

    private suspend fun delete(readingId: Long) {
        val db = Db(this)
        if (db.isWritten(readingId) && !HealthConnect.remove(this, readingId)) return hcFailed()
        db.delete(readingId)
        done(readingId)
    }

    private fun hcFailed() =
        Toast.makeText(this, "Couldn't update Health Connect. Try again.", Toast.LENGTH_LONG).show()

    private fun done(readingId: Long) {
        Notifications.cancel(this, readingId)
        refresh()
    }
}
