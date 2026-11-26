package app.bloblog

import android.app.Activity
import android.content.Context
import android.os.Build
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

/** Call before super.onCreate. On Android 13+ it also tells the system which theme to start with next time, so Dark has no white flash. */
fun Activity.applyAppTheme() {
    val style = ThemePrefs.get(this).style
    setTheme(style)
    if (Build.VERSION.SDK_INT >= 33) splashScreen.setSplashScreenTheme(style)
}

fun Context.token(attr: Int): Int {
    val tv = TypedValue()
    theme.resolveAttribute(attr, tv, true)
    return tv.data
}
