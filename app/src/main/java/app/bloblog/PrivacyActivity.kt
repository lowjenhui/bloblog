package app.bloblog

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/** Health Connect shows this when it asks why Blob-log wants weight access. */
class PrivacyActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        setContentView(TextView(this).apply {
            text = "Blob-log saves your weight from your scale to Health Connect. It doesn't read any Health Connect data, and nothing leaves your phone."
            setPadding(pad, pad, pad, pad)
        })
    }
}
