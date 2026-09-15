package com.scoreorbit.android.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

fun buzz(ctx: Context, millis: Long, amplitude: Int = 255) {
    try {
        val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Full amplitude by default: the system default reads as a
            // whisper on most devices, which defeats tactile dial feedback.
            v.vibrate(VibrationEffect.createOneShot(millis, amplitude.coerceIn(1, 255)))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(millis)
        }
    } catch (_: Exception) { }
}

/** Settings level 1..5 mapped onto a vibration amplitude. */
fun hapticAmplitude(level: Int): Int = (level.coerceIn(1, 5) * 51).coerceIn(1, 255)
