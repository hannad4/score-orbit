package com.scoreorbit.android.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

fun buzz(ctx: Context, millis: Long, level: Int = 100) {
    try {
        val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        // Strength level 0..100. Amplitude alone caps at 255, so the top of
        // the range layers multi-pulse waveforms instead: double- and
        // triple-taps read dramatically stronger than any single blip.
        val clamped = level.coerceIn(0, 100)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amplitude = (12 + clamped * 2.43f).toInt().coerceIn(1, 255)
            val base = (millis * (0.5f + clamped * 0.01f)).toLong().coerceAtLeast(1L)
            val timings: LongArray
            val amps: IntArray
            when {
                clamped < 34 -> {
                    timings = longArrayOf(0, base)
                    amps = intArrayOf(0, amplitude)
                }
                clamped < 67 -> {
                    timings = longArrayOf(0, base, 45, base)
                    amps = intArrayOf(0, amplitude, 0, amplitude)
                }
                else -> {
                    val heavy = (base * 1.4f).toLong()
                    timings = longArrayOf(0, base, 45, base, 45, heavy)
                    amps = intArrayOf(0, amplitude, 0, amplitude, 0, amplitude)
                }
            }
            v.vibrate(VibrationEffect.createWaveform(timings, amps, -1))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(millis)
        }
    } catch (_: Exception) { }
}
