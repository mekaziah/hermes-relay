package com.hermesandroid.relay.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay

/**
 * Frame-throttled stand-in for `rememberInfiniteTransition` for slow, ambient
 * effects — heartbeat dots, banner glows, drifting orbs. Returns a phase that
 * loops `0f → 1f` every [periodMillis], advanced at roughly [fps] instead of
 * the display refresh rate.
 *
 * Why this exists: on Android 15 the platform logs `setRequestedFrameRate` on
 * every Compose draw pass (and on Samsung builds at INFO level). An
 * always-visible `rememberInfiniteTransition` pins the entire window at the
 * panel's refresh (e.g. 120Hz) for as long as it's composed — flooding logcat
 * and burning battery to animate motion the eye can't resolve at full rate
 * anyway. ~30fps is imperceptible for a multi-second pulse.
 *
 * When [running] is false the phase holds at `0f` and the loop parks (no frames
 * requested), so a hidden/idle effect costs nothing.
 *
 * Linear by design (matches the `LinearEasing` + `RepeatMode.Restart` the old
 * infinite transitions used). Map the phase to your value range at the call
 * site, e.g. `1f + 0.8f * phase` for a 1f→1.8f scale.
 *
 * **Timing comes from the wall clock, never `withFrameNanos`.** A throttled loop
 * that awaits the frame clock and then suspends in `delay()` deadlocks after its
 * first step: the state write requests one frame, that frame is drawn while the
 * coroutine is still sleeping, and when it finally re-registers the frame clock
 * has nothing pending — so it waits forever and the animation looks frozen. The
 * write below happens every tick and is itself what schedules the next frame, so
 * a `delay`-only loop is self-sustaining.
 */
@Composable
fun rememberAmbientPhase(
    periodMillis: Int,
    fps: Int = 30,
    running: Boolean = true,
): Float {
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(periodMillis, fps, running) {
        if (!running || periodMillis <= 0) {
            phase.floatValue = 0f
            return@LaunchedEffect
        }
        val frameIntervalMs = (1000L / fps.coerceAtLeast(1)).coerceAtLeast(1L)
        val startNanos = System.nanoTime()
        while (true) {
            val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L
            phase.floatValue = (elapsedMs % periodMillis).toFloat() / periodMillis
            delay(frameIntervalMs)
        }
    }
    return phase.floatValue
}

/**
 * Triangle wave over a [rememberAmbientPhase] value — the shape the old
 * `infiniteRepeatable(tween(n), repeatMode = RepeatMode.Reverse)` transitions
 * produced, at cycle length `2 * n`. Use [offset] to stagger siblings (e.g. the
 * three streaming dots at 1/6 and 1/3 of a cycle).
 *
 * Pure: no frame subscription of its own, so time is driven entirely by the
 * single phase loop already running.
 */
fun ambientTriangle(phase: Float, offset: Float = 0f): Float {
    val p = (phase + offset) % 1f
    return 1f - kotlin.math.abs(2f * p - 1f)
}

/**
 * Drop-in replacement for the `animateFloat(min → max, infiniteRepeatable(tween,
 * Reverse))` pulse that shows up on every loading/working surface: a breathe
 * between [minValue] and [maxValue] taking [periodMillis] for a full there-and-back
 * cycle, advanced at [fps] and parked at [minValue] when [running] is false.
 *
 * Prefer this over `rememberInfiniteTransition` on anything that can be on screen
 * for more than a moment — a tool card, a streaming bubble, a skeleton, the
 * thinking indicator — because an infinite transition pins the window at the
 * display refresh rate for as long as it is composed.
 */
@Composable
fun rememberAmbientBreathe(
    minValue: Float,
    maxValue: Float,
    periodMillis: Int,
    fps: Int = 30,
    running: Boolean = true,
): Float {
    val phase = rememberAmbientPhase(periodMillis = periodMillis, fps = fps, running = running)
    return minValue + (maxValue - minValue) * ambientTriangle(phase)
}
