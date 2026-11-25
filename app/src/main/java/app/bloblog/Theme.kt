package app.bloblog

import android.app.Activity
import android.content.Context
import android.util.TypedValue

enum class AppTheme(val style: Int) {
    COLOURFUL(R.style.Theme_Bloblog_Colourful),
    CALM(R.style.Theme_Bloblog_Calm),
    DARK(R.style.Theme_Bloblog_Dark),
}

object ThemePrefs {
    private fun prefs(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)
    fun get(context: Context): AppTheme =
        AppTheme.values().firstOrNull { it.name == prefs(context).getString("theme", null) } ?: AppTheme.COLOURFUL
    fun set(context: Context, theme: AppTheme) = prefs(context).edit().putString("theme", theme.name).apply()
}

/** Call before super.onCreate. */
fun Activity.applyAppTheme() = setTheme(ThemePrefs.get(this).style)

fun Context.token(attr: Int): Int {
    val tv = TypedValue()
    theme.resolveAttribute(attr, tv, true)
    return tv.data
}
