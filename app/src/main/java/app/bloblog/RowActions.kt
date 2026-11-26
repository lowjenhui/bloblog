package app.bloblog

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The Health Connect calls the row actions need; tests replace it with a fake. */
interface HcPort {
    suspend fun remove(readingId: Long): Boolean
    suspend fun write(r: Reading)
}

private fun healthConnect(a: ComponentActivity) = object : HcPort {
    override suspend fun remove(readingId: Long) = HealthConnect.remove(a, readingId)
    override suspend fun write(r: Reading) = HealthConnect.write(a, r)
}

/** Row sheet, delete confirmation and toasts (spec §5.4 to §5.6). [onChanged] runs after a move or delete so Home can refresh. */
class RowActions(
    private val a: ComponentActivity,
    private val hc: HcPort = healthConnect(a),
    private val onChanged: () -> Unit,
) {
    private val whenFmt = SimpleDateFormat("EEE d MMM · HH:mm", Locale.ENGLISH)
    private fun kg(centi: Int) = "%.2f kg".format(Locale.ROOT, centi / 100.0)

    fun openRowSheet(readingId: Long) {
        val db = Db(a)
        val r = db.reading(readingId) ?: return
        val people = db.people()
        val owner = people.firstOrNull { it.id == r.personId }
        val sheet = BottomSheetDialog(a)
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(a.dp(16), a.dp(12), a.dp(16), a.dp(16)) }

        col.addView(View(a).apply { fill(R.attr.bl_divider, 2) }, LinearLayout.LayoutParams(a.dp(32), a.dp(4)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = a.dp(12) })
        val head = LinearLayout(a).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(ImageView(a).apply {
            setImageResource(if (owner == null) R.drawable.blob_question else R.drawable.blob_scale)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(a.dp(32), a.dp(32)).apply { marginEnd = a.dp(12) })
        val titles = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(label(kg(r.weightCentiKg), 16f, R.attr.bl_text, bold = true))
        titles.addView(label("${whenFmt.format(Date(r.takenAt))} · ${owner?.name ?: "not assigned"}", 12f, R.attr.bl_textMuted))
        head.addView(titles)
        col.addView(head)

        col.addView(label("Move to", 12f, R.attr.bl_textMuted).apply { setPadding(0, a.dp(16), 0, a.dp(4)) })
        for (p in people.filter { it.id != r.personId }) {
            val row = LinearLayout(a).apply {
                gravity = Gravity.CENTER_VERTICAL
                setOnClickListener { sheet.dismiss(); a.lifecycleScope.launch { moveReading(readingId, p.id) } }
            }
            row.addView(label(initials(p.name), 12f, if (p.order == 0) R.attr.bl_ownerText else R.attr.bl_secondText, bold = true).apply {
                gravity = Gravity.CENTER
                fill(if (p.order == 0) R.attr.bl_ownerTint else R.attr.bl_secondTint, 16)
            }, LinearLayout.LayoutParams(a.dp(32), a.dp(32)).apply { marginEnd = a.dp(12) })
            row.addView(label(p.name, 14f, R.attr.bl_text))
            col.addView(row, LinearLayout.LayoutParams(-1, a.dp(48)))
        }
        col.addView(label("🗑 Delete weigh-in", 14f, R.attr.bl_text).apply {
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { sheet.dismiss(); confirmDelete(readingId) }
        }, LinearLayout.LayoutParams(-1, a.dp(48)).apply { topMargin = a.dp(12) })
        sheet.setContentView(col)
        sheet.show()
    }

    /** [onKept] runs if the row stays (cancelled, or Health Connect failed). */
    fun confirmDelete(readingId: Long, onKept: () -> Unit = {}) {
        val r = Db(a).reading(readingId) ?: return onKept()
        val dialog = MaterialAlertDialogBuilder(a)
            .setTitle("Delete this weigh-in?")
            .setMessage("${kg(r.weightCentiKg)} on ${whenFmt.format(Date(r.takenAt)).replace(" · ", " at ")}. If it was sent to Health Connect, it's removed there too. This can't be undone.")
            .setNegativeButton("Cancel") { _, _ -> onKept() }
            .setPositiveButton("Delete") { _, _ -> a.lifecycleScope.launch { if (!deleteReading(readingId)) onKept() } }
            .setOnCancelListener { onKept() }
            .show()
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).apply {
            fill(R.attr.bl_danger, 20); setTextColor(a.token(R.attr.bl_onDanger))
        }
    }

    suspend fun moveReading(readingId: Long, toPersonId: Long): Boolean {
        val db = Db(a)
        val r = db.reading(readingId) ?: return false
        val to = db.people().firstOrNull { it.id == toPersonId } ?: return false
        val wasWritten = db.isWritten(readingId)
        if (wasWritten && !to.isMe && !hc.remove(readingId)) return hcFailed()
        db.setPerson(readingId, toPersonId)
        if (to.isMe && !wasWritten) hc.write(r)
        return done(readingId)
    }

    suspend fun deleteReading(readingId: Long): Boolean {
        val db = Db(a)
        if (db.reading(readingId) == null) return false
        if (db.isWritten(readingId) && !hc.remove(readingId)) return hcFailed()
        db.delete(readingId)
        return done(readingId)
    }

    fun showSaved(name: String, centi: Int) = toast("Saved ${kg(centi)} for $name")
    fun showMatched(count: Int, name: String) = toast("Matched $count earlier weigh-in${if (count == 1) "" else "s"} to $name")

    private fun hcFailed(): Boolean { toast("Couldn't update Health Connect. Try again.", sticker = false); return false }

    private fun done(readingId: Long): Boolean {
        Notifications.cancel(a, readingId)
        onChanged()
        return true
    }

    private fun toast(msg: String, sticker: Boolean = true) {
        val bar = Snackbar.make(a.findViewById(android.R.id.content), msg, 3000)
        bar.view.background = GradientDrawable().apply {
            setColor(a.token(R.attr.bl_toast)); setStroke(a.dp(1), a.token(R.attr.bl_toastStroke)); cornerRadius = a.dp(12).toFloat()
        }
        bar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text).apply {
            setTextColor(a.token(R.attr.bl_onToast))
            if (sticker) {
                val d = a.getDrawable(R.drawable.blob_scale)!!.also { it.setBounds(0, 0, a.dp(24), a.dp(24)) }
                setCompoundDrawablesRelative(d, null, null, null)
                compoundDrawablePadding = a.dp(8)
            }
        }
        bar.show()
    }

    private fun label(t: String, size: Float, attr: Int, bold: Boolean = false) = TextView(a).apply {
        text = t; textSize = size; setTextColor(a.token(attr))
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }
}
