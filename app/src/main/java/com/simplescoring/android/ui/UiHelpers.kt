package com.simplescoring.android.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun buzz(ctx: Context, millis: Long) {
    try {
        val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(millis)
        }
    } catch (_: Exception) { }
}

fun formatDay(timestamp: Long): String {
    val fmt = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
    return fmt.format(Date(timestamp)).uppercase(Locale.getDefault())
}

fun formatDateTime(timestamp: Long): String {
    val fmt = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())
    return fmt.format(Date(timestamp))
}

/** "Today" / "Yesterday" / "March 11, 2024" grouping label for history. */
fun dayLabel(timestamp: Long): String {
    val dayFmt = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    val today = dayFmt.format(Date())
    val yesterday = dayFmt.format(Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000L))
    val key = dayFmt.format(Date(timestamp))
    return when (key) {
        today -> "TODAY"
        yesterday -> "YESTERDAY"
        else -> formatDay(timestamp)
    }
}
