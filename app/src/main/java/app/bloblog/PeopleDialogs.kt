package app.bloblog

import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

/**
 * Add ([p] null) or edit a person. Adding switches to Two people and backfills (spec §5.8, §7.3).
 * [onSaved] gets the name and the number of earlier weigh-ins matched; [onCancel] runs on Cancel or back.
 */
fun ComponentActivity.personDialog(p: Person?, onCancel: () -> Unit = {}, onSaved: (name: String, matched: Int) -> Unit) {
    val name = EditText(this).apply { hint = "Name"; setText(p?.name ?: "") }
    val weight = EditText(this).apply {
        hint = "Starting weight (kg)"
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        if (p != null) setText("%.2f".format(Locale.ROOT, Db(this@personDialog).startWeight(p.id) / 100.0))
    }
    val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), 0); addView(name); addView(weight) }
    val dialog = MaterialAlertDialogBuilder(this)
        .setTitle(if (p == null) "Add person" else "Edit person")
        .setView(form)
        .setNegativeButton("Cancel") { _, _ -> onCancel() }
        .setOnCancelListener { onCancel() }
        .setPositiveButton("Save", null) // real handler below, so invalid input keeps the dialog open
        .show()
    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
        val kg = weight.text.toString().replace(',', '.').toDoubleOrNull()
        when {
            name.text.isBlank() -> name.error = "Enter a name."
            kg == null || kg < 20 || kg > 250 -> weight.error = "Enter a weight between 20 and 250 kg."
            else -> {
                val db = Db(this); val n = name.text.toString().trim(); val centi = Math.round(kg * 100).toInt()
                dialog.setOnCancelListener(null)
                dialog.dismiss()
                if (p != null) { db.updatePerson(p.id, n, centi); onSaved(n, 0) } else {
                    db.addPerson(n, centi, isMe = false)
                    ModePrefs.set(this, PeopleMode.DUO)
                    onSaved(n, db.backfill(db.people().last().id))
                }
            }
        }
    }
}
