package com.simplescoring.android.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

fun buzz(ctx: Context, millis: Long) {
    try {
        val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Full amplitude: the system default reads as a whisper on
            // most devices, which defeats tactile dial feedback.
            v.vibrate(VibrationEffect.createOneShot(millis, 255))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(millis)
        }
    } catch (_: Exception) { }
}
