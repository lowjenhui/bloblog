package app.bloblog

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35]) // Robolectric 4.18.1 has no SDK 36 jar
class ThemeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun clear() { context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit() }

    @Test fun defaultIsColourful() = assertEquals(AppTheme.COLOURFUL, ThemePrefs.get(context))

    @Test fun roundTrip() {
        ThemePrefs.set(context, AppTheme.DARK)
        assertEquals(AppTheme.DARK, ThemePrefs.get(context))
    }

    @Test fun tokensPerTheme() {
        val text = listOf(0xFF3d2f55.toInt(), 0xFF1f2430.toInt(), 0xFFf1eef7.toInt())
        val select = listOf(0xFF3fe3f2.toInt(), 0xFFc4ecf0.toInt(), 0xFF5fd8e8.toInt())
        AppTheme.values().forEachIndexed { i, t ->
            val c = ContextThemeWrapper(context, t.style)
            assertEquals(text[i], c.token(R.attr.bl_text))
            assertEquals(select[i], c.token(R.attr.bl_select))
        }
    }
}
