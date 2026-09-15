package com.scoreorbit.android.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

fun buzz(ctx: Context, millis: Long, level: Int = 10) {
    try {
        val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        // Strength level 1..10 drives both amplitude and duration, so the
        // top end hits clearly harder than a bare max-amplitude blip.
        val clamped = level.coerceIn(1, 10)
        val amplitude = (clamped * 25.5f).toInt().coerceIn(1, 255)
        val scaledMillis = (millis * (0.5f + clamped * 0.1f)).toLong().coerceAtLeast(1L)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(scaledMillis, amplitude))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(scaledMillis)
        }
    } catch (_: Exception) { }
}
