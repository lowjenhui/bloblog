package app.bloblog

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.app.Notification
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Notifications {
    const val EXTRA_READING_ID = "reading_id"

    fun unassigned(context: Context, r: Reading) {
        // Below Android 13 there is no runtime notification permission.
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("unassigned", "Unassigned weigh-ins", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            context, r.id.toInt(),
            Intent(context, MainActivity::class.java).putExtra(EXTRA_READING_ID, r.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(r.takenAt))
        nm.notify(r.id.toInt(), Notification.Builder(context, "unassigned")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Whose weigh-in?")
            .setContentText("%.2f kg at %s — tap to choose.".format(r.weightCentiKg / 100.0, time))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build())
    }

    fun cancel(context: Context, readingId: Long) =
        context.getSystemService(NotificationManager::class.java).cancel(readingId.toInt())
}
