package app.bloblog

import android.content.ContentValues
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CsvExportTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val utc: ZoneId = ZoneOffset.UTC
    private val head = "date,time,weight_kg,person,in_health_connect\n"

    @Before fun fresh() { Db.resetForTests(); context.deleteDatabase("bloblog.db") }

    private fun add(at: Long, centi: Int, person: Long?, written: Boolean = false) {
        Db(context).writableDatabase.insert("reading", null, ContentValues().apply {
            put("taken_at", at); put("weight_centi", centi); put("scale_id", "s"); put("raw_hex", "")
            if (person != null) put("person_id", person); put("hc_written", if (written) 1 else 0)
        })
    }

    @Test fun csvHeaderAndOrder() {
        val db = Db(context); db.addPerson("Julia", 6500, true)
        add(1_700_000_100_000L, 6570, 1, true); add(1_700_000_000_000L, 6565, 1)
        assertEquals(head + "2023-11-14,22:13,65.65,Julia,no\n2023-11-14,22:15,65.70,Julia,yes\n", weighInsCsv(Db(context), utc))
    }

    @Test fun csvUnassignedAndHidden() {
        val db = Db(context); db.addPerson("Julia", 6500, true); db.addPerson("Wei", 7000, false)
        add(1_700_000_000_000L, 5500, null); add(1_700_000_100_000L, 7000, 2)
        assertEquals(head + "2023-11-14,22:13,55.00,,no\n2023-11-14,22:15,70.00,Wei,no\n", weighInsCsv(Db(context), utc))
    }

    @Test fun csvQuotesNames() {
        val db = Db(context); db.addPerson("Stone, \"J\"", 6500, true)
        add(1_700_000_000_000L, 6565, 1)
        assertEquals(head + "2023-11-14,22:13,65.65,\"Stone, \"\"J\"\"\",no\n", weighInsCsv(Db(context), utc))
    }

    @Test fun csvLocalTime() {
        val db = Db(context); db.addPerson("Julia", 6500, true)
        add(1_700_000_000_000L, 6565, 1)
        assertEquals(head + "2023-11-15,06:13,65.65,Julia,no\n", weighInsCsv(Db(context), ZoneId.of("Asia/Singapore")))
    }
}
