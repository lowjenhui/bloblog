package app.bloblog

import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** Settings: People, Appearance and Data (spec §5.3). */
class SettingsActivity : ComponentActivity() {
    private val rows = RowActions(this) {}
    private lateinit var col: LinearLayout
    private var fileName = ""
    private val save = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val csv = weighInsCsv(Db(this@SettingsActivity), ZoneId.systemDefault())
                    contentResolver.openOutputStream(uri)!!.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                }.isSuccess
            }
            // The picker may rename the file, and fileName is lost if the screen was recreated: ask for the real name.
            val shown = contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: fileName
            if (ok) rows.toast("Saved $shown", R.drawable.blob_notebook) else rows.toast("Couldn't save the file. Try again.", null)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyAppTheme()
        super.onCreate(savedInstanceState)
        col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)) }
        setContentView(ScrollView(this).apply { fitsSystemWindows = true; addView(col) })
        render()
    }

    private fun render() {
        col.removeAllViews()
        val db = Db(this)
        val mode = ModePrefs.get(this, db)
        val people = db.activePeople(mode)
        col.addView(text("Settings", 16f, R.attr.bl_text, bold = true))

        col.addView(text("People", 16f, R.attr.bl_text, bold = true), lp(top = 24))
        val intro = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        intro.addView(ImageView(this).apply { setImageResource(R.drawable.blob_wave); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO },
            LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(12) })
        intro.addView(text("Who steps on the scale. Weigh-ins are matched to the closest person.", 14f, R.attr.bl_text))
        col.addView(intro, lp(top = 8))
        col.addView(text("Who weighs in?", 12f, R.attr.bl_textMuted), lp(top = 12))
        col.addView(modeControl(mode, db), lp(top = 4))
        val hcAvailable = HealthConnect.client(this) != null
        people.forEachIndexed { i, p ->
            col.addView(personRow(p, db.startWeight(p.id), hcAvailable))
            if (i < people.lastIndex) col.addView(View(this).apply { fill(R.attr.bl_divider) }, LinearLayout.LayoutParams(-1, dp(1)))
        }
        if (mode == PeopleMode.DUO && people.size < 2) col.addView(text("+ Add person", 14f, R.attr.bl_onSelect).apply {
            gravity = Gravity.CENTER; fill(R.attr.bl_select, 20, inset = true)
            setOnClickListener { editPerson(null) }
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(12) })
        // The per-mode note explains the People section, so it sits under the people list.
        col.addView(text(if (mode == PeopleMode.SOLO) "Weigh-ins far from your weight are held back and ask “Whose?”, so guests and bags never reach Health Connect."
            else "Weigh-ins are matched to the closest person. Only the person marked (Me) is sent to Health Connect.", 12f, R.attr.bl_textMuted), lp(top = 12))

        col.addView(text("Appearance", 16f, R.attr.bl_text, bold = true), lp(top = 32))
        val current = ThemePrefs.get(this)
        for ((t, desc) in listOf(AppTheme.COLOURFUL to "Blob colours and a soft gradient", AppTheme.CALM to "Neutral greys with soft blob colours", AppTheme.DARK to "Easy on the eyes at night")) {
            col.addView(themeRow(t, desc, t == current))
        }

        col.addView(text("Data", 16f, R.attr.bl_text, bold = true), lp(top = 32))
        col.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(56)
            isClickable = true; background = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground)).run { getDrawable(0).also { recycle() } }
            setOnClickListener { fileName = "blob-log-${LocalDate.now()}.csv"; save.launch(fileName) }
            addView(ImageView(this@SettingsActivity).apply { setImageResource(R.drawable.blob_notebook); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO },
                LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(12) })
            addView(LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(text("Download weigh-ins (CSV)", 14f, R.attr.bl_text))
                addView(text("Everyone's weigh-ins, one row each.", 12f, R.attr.bl_textMuted))
            })
        }, lp(top = 8))
    }

    private fun modeControl(mode: PeopleMode, db: Db) = LinearLayout(this).apply {
        fill(R.attr.bl_surfaceGrey, 20, inset = true)
        for ((m, label) in listOf(PeopleMode.SOLO to "Just me", PeopleMode.DUO to "Two people")) {
            val sel = m == mode
            addView(text(label, 14f, if (sel) R.attr.bl_onSelect else R.attr.bl_onGrey).apply {
                gravity = Gravity.CENTER
                if (sel) fill(R.attr.bl_select, 20, inset = true)
                setOnClickListener { if (!sel) switchTo(m, db) }
            }, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
    }

    /** Spec §5.8 "Switching". */
    private fun switchTo(m: PeopleMode, db: Db) {
        if (m == PeopleMode.SOLO) { ModePrefs.set(this, m); return render() }
        if (db.people().size < 2) return editPerson(null) // Cancel leaves Just me
        ModePrefs.set(this, m)
        val second = db.people().first { !it.isMe }
        db.backfill(second.id).takeIf { it > 0 }?.let { rows.showMatched(it, second.name) }
        render()
    }

    private fun personRow(p: Person, startCenti: Int, hcAvailable: Boolean): View {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(16), 0, dp(16))
            setOnClickListener { editPerson(p) }
        }
        row.addView(text(initials(p.name), 14f, p.textAttr(), bold = true).apply {
            gravity = Gravity.CENTER; fill(if (p.order == 0) R.attr.bl_ownerTint else R.attr.bl_secondTint, 20)
        }, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(12) })
        val lines = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        lines.addView(text(p.name + if (p.isMe) " (Me)" else "", 16f, R.attr.bl_text).apply {
            if (p.isMe) text = android.text.SpannableStringBuilder(p.name).append(" (Me)").also {
                it.setSpan(android.text.style.ForegroundColorSpan(token(R.attr.bl_textMuted)), p.name.length, it.length, 0)
            }
        })
        lines.addView(text(when { !p.isMe -> "Saved in Blob-log only"; hcAvailable -> "Sent to Health Connect"; else -> "Health Connect isn't available on this phone" }, 12f, R.attr.bl_textMuted))
        lines.addView(text("Started at %.2f kg".format(Locale.ROOT, startCenti / 100.0), 12f, R.attr.bl_textMuted))
        row.addView(lines, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(text("›", 18f, R.attr.bl_textMuted))
        return row
    }

    private fun themeRow(t: AppTheme, desc: String, selected: Boolean): View {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(12), 0, dp(12))
            setOnClickListener { ThemePrefs.set(this@SettingsActivity, t); recreate() }
        }
        row.addView(RadioButton(this).apply { isChecked = selected; isClickable = false })
        val lines = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        lines.addView(text(t.name.lowercase().replaceFirstChar { it.uppercase() }, 16f, R.attr.bl_text))
        lines.addView(text(desc, 12f, R.attr.bl_textMuted))
        row.addView(lines, LinearLayout.LayoutParams(0, -2, 1f))
        val themed = ContextThemeWrapper(this, t.style) // dots show that theme's colours on any background
        for (attr in listOf(R.attr.bl_select, R.attr.bl_ownerDot, R.attr.bl_secondDot)) {
            row.addView(View(this).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(themed.token(attr)); setStroke(dp(1), themed.token(R.attr.bl_divider))
                }
            }, LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginStart = dp(4) })
        }
        return row
    }

    private fun editPerson(p: Person?) = personDialog(p, onSaved = { n, matched ->
        if (matched > 0) rows.showMatched(matched, n)
        render()
    })

    private fun lp(top: Int) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }

    private fun text(t: String, size: Float, attr: Int, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(token(attr))
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }
}
