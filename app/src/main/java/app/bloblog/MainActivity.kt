package app.bloblog

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

/** Home: person view (spec §5.1). */
class MainActivity : ComponentActivity() {
    private val zone = ZoneId.systemDefault()
    private val dayFmt = java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
    internal var createdTheme = AppTheme.COLOURFUL
    private var setupShowing = false
    private var askedNotifications = false
    private var hcAvailable = false
    private var hcConnected = false
    internal var tab = 0L // person id; -1 is Compare (Task 8)
    internal var range = Range.MONTH
    private var month: YearMonth? = null
    private var shownPts = emptyList<Pt>()
    private var person: Person? = null
    private val rows = RowActions(this) { render() }

    private val hcPermission = registerForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        lifecycleScope.launch { checkHc(); render() }
    }
    private val bluetoothPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        scanState = Scanner.start(this); render()
    }
    // Android throttles >5 scan starts per 30 s and a throttled scan silently stops background capture,
    // so start the scan only on resume and on permission grant, never from render().
    private var scanState = Scanner.State.NO_PERMISSION
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyAppTheme()
        createdTheme = ThemePrefs.get(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val list = findViewById<RecyclerView>(R.id.list)
        list.layoutManager = LinearLayoutManager(this)
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun getSwipeDirs(rv: RecyclerView, vh: RecyclerView.ViewHolder) =
                if ((rv.adapter as? HomeAdapter)?.readingIdAt(vh.bindingAdapterPosition) == null) 0 else super.getSwipeDirs(rv, vh)
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false
            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {
                val adapter = list.adapter as HomeAdapter
                val pos = vh.bindingAdapterPosition
                rows.confirmDelete(adapter.readingIdAt(pos) ?: return) { adapter.notifyItemChanged(pos) }
            }
        }).attachToRecyclerView(list)
        savedInstanceState?.let { s ->
            tab = s.getLong("tab")
            range = Range.valueOf(s.getString("range") ?: Range.MONTH.name)
            month = s.getString("month")?.let { YearMonth.parse(it) }
        }
        if (savedInstanceState == null) openRequested(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong("tab", tab)
        outState.putString("range", range.name)
        month?.let { outState.putString("month", it.toString()) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openRequested(intent)
    }

    /** Opened from the "Whose weigh-in?" notification. */
    private fun openRequested(intent: Intent) {
        val id = intent.getLongExtra(Notifications.EXTRA_READING_ID, -1)
        if (id >= 0) rows.openRowSheet(id)
    }

    // shortcut: the page refreshes only when the screen is opened or resumed (live updates are a later task).
    override fun onResume() {
        super.onResume()
        // Settings changed the theme: recreate so the new theme applies; tab/range/month come back via savedInstanceState.
        if (ThemePrefs.get(this) != createdTheme) return recreate()
        scanState = Scanner.start(this)
        render()
        lifecycleScope.launch { HealthConnect.syncPending(this@MainActivity); checkHc(); render() }
    }

    private suspend fun checkHc() {
        hcAvailable = HealthConnect.client(this) != null
        hcConnected = hcAvailable && HealthConnect.hasPermission(this)
    }

    internal fun selectTab(personId: Long) {
        tab = personId; month = null
        if (personId == -1L && range == Range.DAY) range = Range.WEEK // Compare has no Day pill
        render()
    }

    private fun render() {
        val db = Db(this)
        if (db.me() == null) showSetup(db)
        if (Build.VERSION.SDK_INT >= 33 && scanState == Scanner.State.LISTENING && !askedNotifications &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askedNotifications = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val people = db.people()
        val compare = tab == -1L && people.size >= 2
        val p = if (compare) null else people.firstOrNull { it.id == tab } ?: people.firstOrNull().also { tab = it?.id ?: 0 }
        person = p
        showChips(people, p)
        showStrip(db, p?.isMe == true)
        (findViewById<View>(R.id.headlineWeight).parent as View).visibility = if (compare) View.GONE else View.VISIBLE
        val list = findViewById<RecyclerView>(R.id.list)
        if (compare) {
            val now = System.currentTimeMillis()
            val from = rangeStart(range, now, zone)
            // 7 days before the range start feed the first averages and the baseline
            val pts = people.map { q -> db.readingsFor(q.id, from - 7 * 86_400_000L, now + 1).map { Pt(it.takenAt, it.weightCentiKg) }
                .let { l -> if (l.any { it.at >= from }) l else emptyList() } }
            val series = pts.map { compareSeries(it, from, now, zone) }
            list.adapter = HomeAdapter(listOf(HomeItem.Compare(range, people, pts, series, from, zone)), { range = it; render() }, {}, {}, {})
            return
        }
        if (p == null) { list.adapter = null; return }

        val now = System.currentTimeMillis()
        val pts = db.readingsFor(p.id, rangeStart(range, now, zone), now + 1).map { Pt(it.takenAt, it.weightCentiKg) }
        shownPts = pts
        showHeadline(db, p, null)

        val months = db.months(p.id, zone)
        val items = mutableListOf<HomeItem>(HomeItem.Graph(pts, range, p, db.startWeight(p.id), now))
        if (months.isEmpty()) items.add(HomeItem.Empty) else {
            val m = month?.takeIf { it in months } ?: months.last()
            month = m
            val rows = db.historyMonth(p.id, m, zone)
            items.add(HomeItem.HistoryHeader(rows.count { it.personId != null }, m))
            items.add(HomeItem.Months(months, m))
            rows.forEachIndexed { i, r ->
                val last = i == rows.lastIndex
                items.add(if (r.personId == null) HomeItem.WhoseRow(r, last) else HomeItem.Row(r, p.isMe && !r.written, last))
            }
        }
        list.adapter = HomeAdapter(items,
            onRange = { range = it; render() },
            onMonth = { month = it; render() },
            onRow = rows::openRowSheet,
            onScrub = { showHeadline(db, p, it) })
    }

    private fun showChips(people: List<Person>, current: Person?) {
        val row = findViewById<LinearLayout>(R.id.chips)
        row.removeAllViews()
        fun chip(label: String, selected: Boolean, onClick: () -> Unit) = TextView(this).apply {
            text = label; textSize = 14f; gravity = Gravity.CENTER
            setTextColor(token(if (selected) R.attr.bl_onPersonSelected else R.attr.bl_onGrey))
            fill(if (selected) R.attr.bl_personSelectedBg else R.attr.bl_surfaceGrey, 20, inset = true)
            setOnClickListener { onClick() }
            row.addView(this, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(8) })
        }
        people.forEach { p -> chip(p.name, p.id == current?.id) { selectTab(p.id) } }
        if (people.size >= 2) chip("Compare", current == null) { selectTab(-1) }
        TextView(this).apply {
            text = "⚙"; textSize = 18f; gravity = Gravity.CENTER
            setTextColor(token(R.attr.bl_onGrey)); fill(R.attr.bl_surfaceGrey, 20, inset = true)
            contentDescription = "Settings"
            setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
            row.addView(this, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
    }

    private fun showStrip(db: Db, ownerTab: Boolean) {
        val unassigned = db.unassigned()
        val newest = unassigned.lastOrNull()
        val notice = noticeFor(scanState, unassigned.size, hcAvailable, hcConnected, ownerTab)
        val strip = findViewById<View>(R.id.strip)
        strip.visibility = if (notice == null) View.GONE else View.VISIBLE
        if (notice == null) return
        val button = findViewById<TextView>(R.id.stripButton)
        val chevron = findViewById<View>(R.id.stripChevron)
        val label = when (notice) {
            Notice.NEED_BT_PERMISSION -> "Blob-log listens for your scale in the background."
            Notice.BT_OFF -> "Bluetooth is off. Turn it on, then reopen Blob-log."
            Notice.LISTEN_FAILED -> "Couldn't start listening. Reopen Blob-log to try again."
            Notice.WHOSE -> if (unassigned.size == 1) "Whose is %.2f kg on %s?".format(Locale.ROOT, newest!!.weightCentiKg / 100.0,
                dayFmt.format(Instant.ofEpochMilli(newest.takenAt).atZone(zone))) else "${unassigned.size} weigh-ins need a name"
            Notice.HC_NOT_CONNECTED -> "Health Connect isn't connected. Readings are saved here but not sent."
        }
        val buttonText = when (notice) { Notice.NEED_BT_PERMISSION -> "Allow Bluetooth"; Notice.HC_NOT_CONNECTED -> "Connect"; else -> null }
        findViewById<ImageView>(R.id.stripIcon).setImageResource(when (notice) {
            Notice.NEED_BT_PERMISSION, Notice.BT_OFF -> R.drawable.blob_bluetooth
            Notice.LISTEN_FAILED -> R.drawable.blob_sleep
            Notice.WHOSE -> R.drawable.blob_question
            Notice.HC_NOT_CONNECTED -> R.drawable.blob_heart
        })
        findViewById<TextView>(R.id.stripText).text = label
        button.text = buttonText
        button.visibility = if (buttonText == null) View.GONE else View.VISIBLE
        button.fill(R.attr.bl_select, 20, inset = true)
        button.setOnClickListener {
            if (notice == Notice.HC_NOT_CONNECTED) hcPermission.launch(HealthConnect.PERMISSIONS)
            else bluetoothPermission.launch(Manifest.permission.BLUETOOTH_SCAN)
        }
        chevron.visibility = if (notice == Notice.WHOSE) View.VISIBLE else View.GONE
        strip.setOnClickListener { if (notice == Notice.WHOSE) rows.openRowSheet(newest!!.id) }
        strip.isClickable = notice == Notice.WHOSE
    }

    /** Headline: the person's latest weigh-in, or the weigh-in under the finger while scrubbing ([scrub] is an index into [shownPts]). */
    private fun showHeadline(db: Db, p: Person, scrub: Int?) {
        val weight = findViewById<TextView>(R.id.headlineWeight)
        val line2 = findViewById<TextView>(R.id.headlineLine2)
        findViewById<TextView>(R.id.headlineName).text = p.name
        val point = scrub?.let { shownPts.getOrNull(it) }
        val latest = point ?: db.readingsFor(p.id, 0, Long.MAX_VALUE).lastOrNull()?.let { Pt(it.takenAt, it.weightCentiKg) }
        weight.setTextColor(token(if (point != null) p.textAttr() else R.attr.bl_text))
        if (latest == null) { weight.text = "–"; line2.text = "No weigh-ins yet"; return }
        weight.text = "%.2f kg".format(Locale.ROOT, latest.centi / 100.0)
        val at = Instant.ofEpochMilli(latest.at).atZone(zone)
        if (point != null) { line2.text = java.time.format.DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale.ENGLISH).format(at); return }
        val week = db.readingsFor(p.id, latest.at - 7 * 86_400_000L + 1, latest.at + 1)
        val avg = week.map { it.weightCentiKg / 100.0 }.average()
        val day = if (at.toLocalDate() == java.time.LocalDate.now(zone)) "today" else dayFmt.format(at)
        line2.text = "$day · 7-day avg %.2f".format(Locale.ROOT, avg)
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
            setPadding(dp(16), dp(16), dp(16), 0)
            addView(ImageView(this@MainActivity).apply { setImageResource(R.drawable.blob_wave); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO },
                LinearLayout.LayoutParams(dp(64), dp(64)).apply { gravity = Gravity.CENTER_HORIZONTAL })
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
                    render()
                }
            }
        }
    }
}
