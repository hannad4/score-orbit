package com.simplescoring.android.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplescoring.android.model.Game
import com.simplescoring.android.model.Player
import com.simplescoring.android.util.RotationUtils
import com.simplescoring.android.viewmodel.AppScreen
import com.simplescoring.android.viewmodel.ScoreViewModel
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Seat angle in radians for [index] of [total] (clockwise, y-down). Seat 0
 * sits at the top by default. Only a [total] that's a multiple of 4 always
 * lands a seat exactly at due east/west (90°/270°) — the tightest, most
 * ambiguous spot against the screen edge — so only those counts rotate the
 * whole ring back by half a seat's spacing (-π/total), landing seat 0
 * top-left instead of dead top. Other counts (even or odd) never hit that
 * exact side extremum, so they keep the plain top start.
 */
private fun seatAngle(index: Int, total: Int): Double {
    val origin = if (total >= 4 && total % 4 == 0) -PI / 2 - PI / total else -PI / 2
    return origin + index * 2.0 * PI / total
}

private fun nearestSeat(point: Offset, center: Offset, total: Int): Int {
    val a = atan2((point.y - center.y).toDouble(), (point.x - center.x).toDouble())
    var best = 0
    var bestD = Double.MAX_VALUE
    for (i in 0 until total) {
        var d = abs(a - seatAngle(i, total)) % (2 * PI)
        if (d > PI) d = 2 * PI - d
        if (d < bestD) {
            bestD = d
            best = i
        }
    }
    return best
}

/**
 * Find nearest anchor (non-side) seat index to [i], searching both
 * directions. Only considers anchors in the same top/bottom half as [i]
 * ([topHalf]) so a bottom-dwelling seat never gets pulled up to stack near a
 * top anchor (or vice versa) just because it's the closest one by index.
 */
private fun findNearestAnchor(i: Int, n: Int, isSide: BooleanArray, topHalf: BooleanArray): Int {
    for (offset in 1..n / 2) {
        val cw = (i + offset) % n
        val ccw = (i - offset + n) % n
        if (!isSide[cw] && topHalf[cw] == topHalf[i]) return cw
        if (!isSide[ccw] && topHalf[ccw] == topHalf[i]) return ccw
    }
    return -1
}

/**
 * Hand-placed label angles (degrees; 0 = screen right, 90 = down, matching
 * [seatAngle]'s convention) for [n] players, one per player index in order.
 * Used for 4-6 players only (7+ uses the top/bottom half layout instead —
 * see the ScoreboardScreen composable): with only 6 "roomy" compass spots
 * that don't crowd the central dial (the 4 diagonals plus straight up/down
 * — due left/right is always too tight against the ring on a phone), this
 * fills those spots first. Player dots on the ring stay evenly spaced
 * regardless — only the score labels use this layout — but the walk around
 * the 6 spots starts at whichever one seat 0's dot actually sits on, so a
 * label always lines up with its own dot: top-left for a multiple of 4 (see
 * [seatAngle]'s half-step shift), straight up otherwise.
 */
private fun manualLabelAngles(n: Int): List<Double>? {
    // Depth (0 or 1) of each of the 6 roomy compass slots, in fixed order
    // [N, NE, SE, S, SW, NW].
    val depths = when (n) {
        4 -> intArrayOf(0, 1, 1, 0, 1, 1)
        5 -> intArrayOf(1, 1, 1, 0, 1, 1)
        6 -> intArrayOf(1, 1, 1, 1, 1, 1)
        else -> return null
    }
    val slotAngle = doubleArrayOf(-90.0, -45.0, 45.0, 90.0, 135.0, -135.0) // N, NE, SE, S, SW, NW
    val order = if (n % 4 == 0) intArrayOf(5, 0, 1, 2, 3, 4) else intArrayOf(0, 1, 2, 3, 4, 5)
    val result = mutableListOf<Double>()
    for (slot in order) if (depths[slot] >= 1) result += slotAngle[slot]
    return result
}

/**
 * Distance from [center] along [dir] to the inside of the rect
 * [0, w]x[0, h] shrunk by [margin]. Used to push score labels out to the
 * screen edges for the tabletop layout.
 */
private fun rayToEdge(center: Offset, dir: Offset, w: Float, h: Float, margin: Float): Float {
    var t = Float.MAX_VALUE
    if (dir.x > 1e-6f) t = min(t, (w - margin - center.x) / dir.x)
    else if (dir.x < -1e-6f) t = min(t, (margin - center.x) / dir.x)
    if (dir.y > 1e-6f) t = min(t, (h - margin - center.y) / dir.y)
    else if (dir.y < -1e-6f) t = min(t, (margin - center.y) / dir.y)
    return if (t.isFinite()) t.coerceAtLeast(0f) else 0f
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoreboardScreen(game: Game, viewModel: ScoreViewModel) {
    val ctx = LocalContext.current
    var latestGame by remember { mutableStateOf(game) }
    latestGame = game

    var activeId by remember(game.id) { mutableStateOf<String?>(null) }
    var pending by remember(game.id) { mutableIntStateOf(0) }
    var accRadians by remember(game.id) { mutableFloatStateOf(0f) }
    var lastAngle by remember { mutableFloatStateOf(Float.NaN) }
    // Last committed action, flashed in the middle of the ring as name +
    // final delta, fading out on its own after ~2s.
    var lastFlash by remember(game.id) { mutableStateOf<Triple<Int, Int, String>?>(null) }
    var flashAlpha by remember(game.id) { mutableFloatStateOf(0f) }
    // Settle fade 0->1 after the rewind: bead fades out while the hidden
    // dots fade back in, so nothing pops.
    var settle by remember(game.id) { mutableFloatStateOf(0f) }
    // Slow trail dissolve 1->0 after settling: the sweep color eases back
    // to gray instead of blinking out.
    var trailDissolve by remember(game.id) { mutableFloatStateOf(1f) }
    // Marker grow 0->1 on grab (only when enlargement is on).
    var markerScale by remember(game.id) { mutableFloatStateOf(0f) }
    var markerJob by remember(game.id) { mutableStateOf<Job?>(null) }
    var flashJob by remember(game.id) { mutableStateOf<Job?>(null) }
    // In-flight "spring back" animation that unwinds the dial after release.
    val scope = rememberCoroutineScope()
    var springJob by remember(game.id) { mutableStateOf<Job?>(null) }

    val turn = game.rotationPoints.coerceAtLeast(1)
    val activeIndex = game.players.indexOfFirst { it.id == activeId }
    val activePlayer = activeIndex.takeIf { it >= 0 }?.let { game.players[it] }

    fun resetGesture() {
        springJob?.cancel()
        springJob = null
        markerJob?.cancel()
        markerJob = null
        flashJob?.cancel()
        flashJob = null
        lastFlash = null
        flashAlpha = 0f
        settle = 0f
        trailDissolve = 1f
        markerScale = 0f
        activeId = null
        pending = 0
        accRadians = 0f
        lastAngle = Float.NaN
    }

    fun showFlash(color: Int, delta: Int, name: String) {
        // NOTE: like commitGesture, this runs from pointer-input and tap
        // handlers that may hold stale compositions: only State reads here.
        // Zero-point taps flash nothing.
        if (delta == 0) return
        flashJob?.cancel()
        lastFlash = Triple(color, delta, name)
        flashAlpha = 1f
        if (!latestGame.keepLastVisible) {
            // Brief flash then fade ("off" = transient display)
            flashJob = scope.launch {
                delay(800)
                animate(
                    initialValue = 1f,
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 300),
                ) { value, _ -> flashAlpha = value }
                lastFlash = null
                flashJob = null
            }
        }
        // "on" = persistent: flash stays until next scoring event
    }

    fun commitGesture() {
        // NOTE: this is called from inside pointerInput, whose block is not
        // restarted mid-gesture. It must only read State (current at call
        // time), never plain vals captured from an old composition.
        val id = activeId
        val delta = pending
        val g = latestGame
        val player = g.players.firstOrNull { it.id == id }
        if (id != null && delta != 0) {
            viewModel.addScore(id, delta)
            // Totals are committed: drop the live readout immediately so the
            // center switches to the flash instead of showing stale pending
            // through the unwind. activeId stays until the spring settles so
            // the dots/trail keep animating back in place.
            pending = 0
            if (player != null) showFlash(player.color, delta, player.name)
        }
        lastAngle = Float.NaN

        // Like a real rotary dial's return spring: rewind opposite the
        // spin just until the dots sit back on their start coordinates,
        // then snap the (rotation-identical) remainder to zero. Only the
        // visible offset ever travels — whole completed laps already sit
        // at home, so they don't need to spin back.
        springJob?.cancel()
        val twoPi = 2f * PI.toFloat()
        val start = accRadians
        val home = if (start >= 0f) floor(start / twoPi) * twoPi
                   else ceil(start / twoPi) * twoPi
        springJob = scope.launch {
            if (abs(start - home) >= 0.05f) {
                animate(
                    initialValue = start,
                    targetValue = home,
                    // Slow, stately rotary return: settling time scales with
                    // 1/sqrt(stiffness), so ~70 takes roughly 4-5x as long as
                    // StiffnessMedium (1500) to settle.
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = 70f,
                    ),
                ) { value, _ -> accRadians = value }
                // NOTE: accRadians deliberately stays at home (rotation-
                // equivalent to zero) through the fade below, so the trail
                // keeps its full final sweep to dissolve from.
            } else {
                // Already (visually) home: nothing to unwind.
                accRadians = 0f
            }
            // Smooth handoff instead of snapping to gray, in two phases.
            // Phase 1: the bead shrinks out while the hidden dots fade
            // back in. Phase 2: the sweep color itself dissolves slowly
            // back to the gray ring. Runs after every release — even tiny
            // flicks — so nothing ever pops.
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = tween(durationMillis = 350),
            ) { value, _ -> settle = value }
            animate(
                initialValue = 1f,
                targetValue = 0f,
                animationSpec = tween(durationMillis = 900),
            ) { value, _ -> trailDissolve = value }
            accRadians = 0f
            settle = 0f
            trailDissolve = 1f
            markerScale = 0f
            activeId = null
            springJob = null
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        game.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.go(AppScreen.ScoreHistory) }) {
                            Icon(Icons.Default.History, contentDescription = "Score history")
                        }
                        IconButton(onClick = { viewModel.go(AppScreen.Leaderboard) }) {
                            Icon(Icons.Default.Leaderboard, contentDescription = "Leaderboard")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.go(AppScreen.Settings) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val density = LocalDensity.current
            val wPx = with(density) { maxWidth.toPx() }
            val hPx = with(density) { maxHeight.toPx() }
            if (min(wPx, hPx) <= 0f || game.players.isEmpty()) return@BoxWithConstraints

            val cx = wPx / 2f
            val cy = hPx / 2f
            val center = Offset(cx, cy)
            val n = game.players.size
            val minDim = min(wPx, hPx)
            fun dp(px: Float): Dp = with(density) { px.toDp() }

            // Ring sized for the narrow side; dots sit on it. Both run 15% slimmed
            // down from raw scale so the dial doesn't crowd the labels.
            val ringR = minDim * 0.34f
            val share = (2 * PI.toFloat() * ringR / n) * 0.68f
            val dotD = (share.coerceIn(
                with(density) { 34.dp.toPx() },
                with(density) { 64.dp.toPx() },
            )) * 0.85f
            // Track exactly matches the dot diameter (derived from the same
            // already-clamped value, never capped separately) so the ring
            // and the colored circles are always the same thickness.
            val trackWidth = dotD

            // Score boxes: rotation-proof squares, sized by player count.
            // 4-6 players sit at diagonal corners (see manualLabelAngles),
            // which eat into both screen dimensions at once — a smaller box
            // than the old n<=4 tier leaves real breathing room between the
            // box and the ring instead of pinning it right to the screen
            // edge with almost no gap. 7+ players lay a crowded half of the
            // wheel out as side-by-side columns (see the halfLayout below)
            // instead, which needs the box to actually shrink as the widest
            // half grows, or neighboring columns start to overlap. Rather
            // than guess a static size per count, size it directly from the
            // real screen width so it's as large as it can be while still
            // leaving a small gap between columns.
            val edgeMarginPx = with(density) { 8.dp.toPx() }
            val labelBoxPx = when {
                n <= 3 -> with(density) { 140.dp.toPx() }
                n <= 6 -> with(density) { 100.dp.toPx() }
                else -> {
                    val maxHalfK = (0 until n).groupBy { sin(seatAngle(it, n)) <= 0.0 }.values.maxOf { it.size }
                    if (maxHalfK < 4) {
                        with(density) { 100.dp.toPx() }
                    } else {
                        val usableWidth = wPx - 2 * edgeMarginPx
                        val gap = with(density) { 4.dp.toPx() }
                        // 4 columns (7-8 players) comfortably fits a 95dp
                        // floor on a typical phone; 5-6 columns (9-12
                        // players) can't without overlapping, so they stay
                        // governed purely by the width-fit computation.
                        val floor = with(density) { if (maxHalfK <= 4) 95.dp.toPx() else 56.dp.toPx() }
                        ((usableWidth - gap * (maxHalfK - 1)) / maxHalfK)
                            .coerceIn(floor, with(density) { 100.dp.toPx() })
                    }
                }
            }
            val scoreSp = (labelBoxPx * 0.44f / density.density).coerceIn(20f, 64f)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(game.id, game.rotationPoints) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val g = latestGame
                                if (g.players.isEmpty()) return@detectDragGestures
                                // Grabbing the dial again mid spring-back cancels the
                                // return animation instead of fighting it for control
                                // of accRadians.
                                resetGesture()
                                val seat = nearestSeat(offset, center, g.players.size)
                                activeId = g.players[seat].id
                                // Grow the touch marker in on grab (only when
                                // enlargement is on — otherwise it stays put).
                                if (g.enlargeActiveDot) {
                                    markerJob?.cancel()
                                    markerJob = scope.launch {
                                        animate(
                                            initialValue = 0f,
                                            targetValue = 1f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = 500f,
                                            ),
                                        ) { value, _ -> markerScale = value }
                                    }
                                }
                                lastAngle = atan2(
                                    (offset.y - cy).toDouble(),
                                    (offset.x - cx).toDouble()
                                ).toFloat()
                                accRadians = 0f
                                pending = 0
                            },
                            onDragEnd = { commitGesture() },
                            // A cancelled gesture (incoming call, etc.) still
                            // keeps completed points instead of dropping them.
                            onDragCancel = { commitGesture() },
                            onDrag = { change, _ ->
                                change.consume()
                                if (activeId == null) return@detectDragGestures
                                if (lastAngle.isNaN()) {
                                    lastAngle = atan2(
                                        (change.position.y - cy).toDouble(),
                                        (change.position.x - cx).toDouble()
                                    ).toFloat()
                                    return@detectDragGestures
                                }
                                val angle = atan2(
                                    (change.position.y - cy).toDouble(),
                                    (change.position.x - cx).toDouble()
                                ).toFloat()
                                var delta = angle - lastAngle
                                while (delta > PI.toFloat()) delta -= 2f * PI.toFloat()
                                while (delta < -PI.toFloat()) delta += 2f * PI.toFloat()
                                if (abs(delta) > PI.toFloat() / 2f) {
                                    lastAngle = angle
                                    return@detectDragGestures
                                }
                                lastAngle = angle
                                accRadians += delta
                                val p = (accRadians / (2f * PI.toFloat()) * turn).roundToInt()
                                if (p != pending) {
                                    pending = p
                                    buzz(ctx, 6)
                                }
                            }
                        )
                    }
            ) {
                // Track ring + swipe arc (bounded canvas: cheap frames).
                RingDial(
                    cxPx = cx,
                    cyPx = cy,
                    ringRPx = ringR,
                    trackPx = trackWidth,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    activeColor = activePlayer?.let { Color(it.color) },
                    showMarker = game.enlargeActiveDot,
                    settleAlpha = settle,
                    markerScale = markerScale,
                    trailAlpha = trailDissolve,
                    // Arc trails from the player's dot along the drag. Not
                    // clamped to one lap: RingDial itself turns anything
                    // beyond 360° into a stacked, full-circle fade instead of
                    // truncating the visual at the first turn.
                    arcStartDeg = activeIndex.takeIf { it >= 0 }
                        ?.let { (seatAngle(it, n) * 180.0 / PI).toFloat() } ?: -90f,
                    arcSweepDeg = accRadians * 180f / PI.toFloat(),
                )

                // Center readout: one slot shared by the live spin readout
                // and the post-commit flash, so stale text can never linger.
                // While turning: name + live delta. On release the same
                // name + final delta keeps showing (now fading). Empty
                // otherwise. In particular the flash takes over the moment
                // pending hits zero — including through the unwind, when the
                // gesture is technically still settling.
                val livePlayer = activePlayer
                val flash = lastFlash
                // With "keep last visible" off, nothing static may linger in
                // the center: no touch-down name, no unwind name, no flash.
                // The live readout only ever shows while points accumulate.
                val keepLast = game.keepLastVisible
                val showLive = livePlayer != null && (pending != 0 || (flash == null && keepLast))
                if (showLive && livePlayer != null) {
                    val c = Color(livePlayer.color)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.align(Alignment.Center),
                    ) {
                        Text(
                            text = livePlayer.name,
                            fontSize = (scoreSp * 0.32f).coerceAtLeast(12f).sp,
                            color = c,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                        if (pending != 0) {
                            Text(
                                text = if (pending > 0) "+$pending" else "$pending",
                                fontSize = (scoreSp * 1.1f).sp,
                                fontWeight = FontWeight.Bold,
                                color = c,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                } else if (flash != null && flashAlpha > 0f) {
                    val (colorInt, delta, name) = flash
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .alpha(flashAlpha)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { viewModel.go(AppScreen.ScoreHistory) },
                            ),
                    ) {
                        Text(
                            text = name,
                            fontSize = (scoreSp * 0.32f).coerceAtLeast(12f).sp,
                            color = Color(colorInt),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = if (delta > 0) "+$delta" else "$delta",
                            fontSize = (scoreSp * 1.1f).sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(colorInt),
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                game.players.forEachIndexed { i, player ->
                    // While dragging, the whole wheel of dots turns together
                    // with the touch (a real rotary dial's disk), rather than
                    // just the active player's own trail moving in place.
                    // Idle dots hide for the spin and fade back in with the
                    // settle animation instead of popping.
                    val isActiveDot = activeId == null || activeId == player.id
                    val a = if (activeId != null) seatAngle(i, n) + accRadians else seatAngle(i, n)
                    SeatDot(
                        color = Color(player.color),
                        sizeDp = dp(dotD),
                        alpha = if (isActiveDot) 1f else settle,
                        offsetPx = IntOffset(
                            x = (cx + cos(a).toFloat() * ringR - dotD / 2f).toInt(),
                            y = (cy + sin(a).toFloat() * ringR - dotD / 2f).toInt(),
                        ),
                        onTap = {
                            buzz(ctx, 12)
                            viewModel.addScore(player.id, game.tapPoints)
                            showFlash(player.color, game.tapPoints, player.name)
                        },
                    )
                }

                // Scores pushed out to the screen edges. 1-3 players use the
                // seatAngle-driven layout below (anchors at their natural
                // rotary start point; side players stack off a same-half
                // anchor, or off their own ring-edge spot when there isn't
                // one). 4-6 players use the hand-placed compass layout from
                // manualLabelAngles. 7+ players split into top/bottom halves
                // (by each seat's own vertical direction) and, per half:
                // fewer than 4 players still anchor along their own ray, same
                // as 1-3 players; 4 or more spread out as evenly spaced
                // columns across the screen — in the seats' left-to-right
                // order, alternating a near row (just past the ring) and a
                // far row (screen edge) — since a half that crowded usually
                // has no natural anchor left to stack from.
                val labelPositions = arrayOfNulls<Offset>(n)
                val stackOffset = labelBoxPx + with(density) { 12.dp.toPx() }
                // Minimum breathing room between a label and the ring/dots so
                // crowded boards never read as clipped into the dial.
                val labelClearPx = with(density) { 20.dp.toPx() }
                val manualAngles = manualLabelAngles(n)
                if (n >= 7) {
                    val nearD = ringR + dotD / 2f + labelClearPx
                    val farD = nearD + stackOffset
                    val xMin = edgeMarginPx + labelBoxPx / 2f
                    val xMax = wPx - edgeMarginPx - labelBoxPx / 2f
                    for (top in booleanArrayOf(true, false)) {
                        val half = (0 until n).filter { (sin(seatAngle(it, n)) <= 0.0) == top }
                        if (half.size < 4) {
                            for (i in half) {
                                val a = seatAngle(i, n)
                                val dirX = cos(a).toFloat()
                                val dirY = sin(a).toFloat()
                                val dotX = cx + dirX * ringR
                                val dotY = cy + dirY * ringR
                                if (abs(dirX) >= cos(40 * PI / 180).toFloat()) {
                                    // Side seat: no room for a full label box
                                    // further out on the ray — it would land
                                    // on top of its own dot. Stack vertically
                                    // off the dot instead (up for top-half
                                    // seats, down for bottom-half ones).
                                    val above = sin(a) <= 0.0
                                    val y = dotY + (if (above) -1f else 1f) *
                                        (dotD / 2f + labelClearPx + labelBoxPx / 2f)
                                    labelPositions[i] = Offset(dotX, y)
                                    continue
                                }
                                val maxDist = rayToEdge(center, Offset(dirX, dirY), wPx, hPx, edgeMarginPx + labelBoxPx / 2f)
                                val dist = min(1.85f * ringR, maxDist).coerceAtLeast(nearD)
                                labelPositions[i] = Offset(cx + dirX * dist, cy + dirY * dist)
                            }
                        } else {
                            val ordered = half.sortedBy { cos(seatAngle(it, n)) }
                            val k = ordered.size
                            // Outer seats (by left-to-right order) sit in the
                            // near row, inner ones in the far row for 4 (an
                            // inverted "smile" — on review this read better
                            // than outer-far); a strict zigzag from 5 up.
                            val isFar = when (k) {
                                4 -> booleanArrayOf(false, true, true, false)
                                5 -> booleanArrayOf(true, false, true, false, true)
                                else -> BooleanArray(k) { it % 2 == 0 }
                            }
                            val sign = if (top) -1f else 1f
                            // Both rows push out an extra half box-height from
                            // the centerline, so a fully split board (4+ on
                            // both halves) doesn't read as one crowded band
                            // straddling the middle of the screen.
                            val centerlineClearance = labelBoxPx / 2f
                            // Fit both rows into the room above/below the
                            // ring: shrink them toward the dial
                            // proportionally when the far row would leave the
                            // screen, so labels never clip at the edges.
                            val vRoom = (min(cy, hPx - cy) - edgeMarginPx - labelBoxPx / 2f)
                                .coerceAtLeast(0f)
                            val vWant = farD + centerlineClearance
                            val vFit = if (vWant > vRoom && vWant > 0f) {
                                (vRoom / vWant).coerceIn(0.2f, 1f)
                            } else 1f
                            ordered.forEachIndexed { j, i ->
                                val x = xMin + j * (xMax - xMin) / (k - 1)
                                val d = ((if (isFar[j]) farD else nearD) + centerlineClearance) * vFit
                                labelPositions[i] = Offset(x, cy + sign * d)
                            }
                        }
                    }
                } else if (manualAngles != null) {
                    val used = HashMap<Double, Int>()
                    for (i in 0 until n) {
                        val angleDeg = manualAngles[i]
                        val a = angleDeg * PI / 180.0
                        val dirX = cos(a).toFloat()
                        val dirY = sin(a).toFloat()
                        val maxDist = rayToEdge(center, Offset(dirX, dirY), wPx, hPx, edgeMarginPx + labelBoxPx / 2f)
                        val dist = min(1.85f * ringR, maxDist)
                            .coerceAtLeast(ringR + dotD / 2f + labelClearPx)
                        val occurrence = used.getOrDefault(angleDeg, 0)
                        used[angleDeg] = occurrence + 1
                        // coerceAtMost keeps a doubled-up slot's outer occupant
                        // from being pushed past the screen edge.
                        val finalDist = (dist + stackOffset * occurrence).coerceAtMost(maxDist)
                        labelPositions[i] = Offset(cx + dirX * finalDist, cy + dirY * finalDist)
                    }
                } else {
                    // Anchors (valid, non-side positions) stay put on their
                    // natural rotary start point. Side players — those whose
                    // horizontal component |cos(theta)| clears the threshold,
                    // i.e. living on the left/right of the wheel — stack
                    // above/below their nearest anchor instead of overlapping
                    // the central UI.
                    val sideThreshold = cos(40 * PI / 180).toFloat() // |cos(angle)| above this = side player
                    val isSide = BooleanArray(n) { abs(cos(seatAngle(it, n)).toFloat()) >= sideThreshold }
                    // Which half of the wheel a seat's own position falls in
                    // — a side player stacks within its own half, never the
                    // other.
                    val topHalf = BooleanArray(n) { sin(seatAngle(it, n)) <= 0.0 }

                    // Place anchors (non-side players) at their natural positions.
                    for (i in 0 until n) {
                        if (isSide[i]) continue
                        val a = seatAngle(i, n)
                        val dirX = cos(a).toFloat()
                        val dirY = sin(a).toFloat()
                        val maxDist = rayToEdge(center, Offset(dirX, dirY), wPx, hPx, edgeMarginPx + labelBoxPx / 2f)
                        val dist = min(1.85f * ringR, maxDist)
                            .coerceAtLeast(ringR + dotD / 2f + labelClearPx)
                        labelPositions[i] = Offset(cx + dirX * dist, cy + dirY * dist)
                    }

                    // For each side player, resolve the point it stacks from and
                    // whether it belongs above or below that point. This only
                    // looks at true anchors (isSide == false) in the same half
                    // of the wheel, never at other side players or the opposite
                    // half, so the result doesn't depend on processing order and
                    // a bottom-half player never gets thrown to the top.
                    val anchorPosOf = arrayOfNulls<Offset>(n)
                    val aboveOf = BooleanArray(n)
                    val diffOf = DoubleArray(n)
                    for (i in 0 until n) {
                        if (!isSide[i]) continue
                        val a = seatAngle(i, n)
                        val dirX = cos(a).toFloat()
                        val dirY = sin(a).toFloat()
                        val anchorIdx = findNearestAnchor(i, n, isSide, topHalf)
                        if (anchorIdx < 0) {
                            // No anchor in this half of the wheel; stack out from
                            // the seat's own ring-edge position instead of
                            // reaching across to an anchor on the other half.
                            val maxDist = rayToEdge(center, Offset(dirX, dirY), wPx, hPx, edgeMarginPx + labelBoxPx / 2f)
                            val dist = min(1.85f * ringR, maxDist)
                                .coerceAtLeast(ringR + dotD / 2f + labelClearPx)
                            anchorPosOf[i] = Offset(cx + dirX * dist, cy + dirY * dist)
                            aboveOf[i] = topHalf[i]
                            diffOf[i] = 0.0
                            continue
                        }
                        val anchorAngle = seatAngle(anchorIdx, n)
                        var angleDiff = a - anchorAngle
                        while (angleDiff > PI) angleDiff -= 2 * PI
                        while (angleDiff < -PI) angleDiff += 2 * PI
                        val onRightSide = dirX > 0
                        anchorPosOf[i] = labelPositions[anchorIdx]
                        aboveOf[i] = if (onRightSide) angleDiff > 0 else angleDiff < 0
                        diffOf[i] = abs(angleDiff)
                    }

                    // Stack each side player above/below its reference point,
                    // ranked by angular closeness so players sharing a reference
                    // point + direction land at increasing distances instead of
                    // on top of each other — always at least one full step out,
                    // so even a lone side player clears the central dial.
                    for (i in 0 until n) {
                        if (!isSide[i]) continue
                        val anchorPos = anchorPosOf[i] ?: continue
                        var rank = 1
                        for (j in 0 until n) {
                            if (j == i || !isSide[j] || anchorPosOf[j] != anchorPos || aboveOf[j] != aboveOf[i]) continue
                            if (diffOf[j] < diffOf[i] || (diffOf[j] == diffOf[i] && j < i)) rank++
                        }
                        val totalOffset = stackOffset * rank
                        labelPositions[i] = Offset(
                            anchorPos.x,
                            if (aboveOf[i]) anchorPos.y - totalOffset else anchorPos.y + totalOffset
                        )
                    }
                }

                // Render all labels.
                // Final guard for every layout above: no label may leave the
                // screen. Crowded boards overlap slightly instead of
                // clipping cut-off text at the edges.
                fun clampAxis(v: Float, size: Float): Float {
                    val lo = labelBoxPx / 2f + edgeMarginPx
                    val hi = size - labelBoxPx / 2f - edgeMarginPx
                    return if (hi <= lo) size / 2f else v.coerceIn(lo, hi)
                }
                game.players.forEachIndexed { i, player ->
                    val raw = labelPositions[i]!!
                    val pos = Offset(clampAxis(raw.x, wPx), clampAxis(raw.y, hPx))
                    val isActive = activeId == player.id
                    val showingPending = isActive && pending != 0
                    SeatScore(
                        player = player,
                        // While swiping, the score shows the live pending delta.
                        text = when {
                            showingPending && pending > 0 -> "+$pending"
                            showingPending -> "$pending"
                            else -> "${game.currentScore(player.id)}"
                        },
                        showName = game.showPlayerNames,
                        scoreSp = scoreSp,
                        boxDp = dp(labelBoxPx),
                        dimmed = activeId != null && !isActive,
                        offsetPx = IntOffset(
                            x = (pos.x - labelBoxPx / 2f).toInt(),
                            y = (pos.y - labelBoxPx / 2f).toInt(),
                        ),
                        onTap = {
                            buzz(ctx, 10)
                            viewModel.rotatePlayer(player.id)
                        },
                    )
                }
            }
        }
    }
}

/**
 * Track ring with the in-progress swipe arc. Bounded to the ring diameter so
 * drag frames repaint a small canvas instead of the whole screen.
 */
@Composable
private fun RingDial(
    cxPx: Float,
    cyPx: Float,
    ringRPx: Float,
    trackPx: Float,
    trackColor: Color,
    activeColor: Color?,
    showMarker: Boolean,
    settleAlpha: Float,
    markerScale: Float,
    trailAlpha: Float,
    arcStartDeg: Float,
    arcSweepDeg: Float,
) {
    val density = LocalDensity.current
    val diameterPx = ringRPx * 2f + trackPx + with(density) { 4.dp.toPx() }
    Canvas(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = (cxPx - diameterPx / 2f).toInt(),
                    y = (cyPx - diameterPx / 2f).toInt(),
                )
            }
            .size(with(density) { diameterPx.toDp() })
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            color = trackColor,
            radius = ringRPx,
            center = center,
            style = Stroke(width = trackPx),
        )
        if (activeColor != null) {
            val tipDeg = arcStartDeg + arcSweepDeg
            // Slow dissolve for the whole trail (not just the bead): after a
            // multi-turn spin the sweep stays past 360° through the entire
            // rewind, so without this the full colored ring would blink out
            // in a single frame at release instead of easing back to gray.
            if (arcSweepDeg != 0f && trailAlpha > 0f) {
                // Comet trail: full opacity at the touch point, fading to
                // 10% one full turn behind it. Sampled as a sweep gradient
                // fixed in absolute canvas angle (not rotated to the moving
                // tip), so drawArc/drawCircle can just sample it directly.
                // Past one lap the whole ring is covered by the same
                // periodic fade, which is what makes extra laps "stack"
                // instead of erasing the earlier trail.
                val trailBrush = Brush.sweepGradient(
                    colors = trailFadeColors(activeColor, tipDeg, clockwise = arcSweepDeg >= 0f),
                    center = center,
                )
                if (abs(arcSweepDeg) >= 360f) {
                    drawCircle(
                        brush = trailBrush,
                        radius = ringRPx,
                        center = center,
                        alpha = trailAlpha,
                        style = Stroke(width = trackPx),
                    )
                } else {
                    // Negative sweeps don't rasterize on this canvas path,
                    // so counter-clockwise drags are redrawn as the
                    // equivalent positive sweep back from the tip. The
                    // visible trail and tip position are identical either
                    // way — this just guarantees both directions render.
                    val start = if (arcSweepDeg >= 0f) arcStartDeg else tipDeg
                    // Round cap: the trailing (oldest) end rounds off instead
                    // of a flat cut-off. The tip end is rounded too, but the
                    // touch-marker disk drawn below fully covers it either way.
                    drawArc(
                        brush = trailBrush,
                        startAngle = start,
                        sweepAngle = abs(arcSweepDeg),
                        useCenter = false,
                        topLeft = Offset(center.x - ringRPx, center.y - ringRPx),
                        size = Size(ringRPx * 2f, ringRPx * 2f),
                        alpha = trailAlpha,
                        style = Stroke(width = trackPx, cap = StrokeCap.Round),
                    )
                }
            }

            // Touch-marker disk: grows tiny-to-large on grab, then shrinks
            // back out with the settle animation instead of popping. Gated
            // by the "enlarge active dot" setting — off means a
            // fixed-diameter dial.
            if (!showMarker) return@Canvas
            val beadAlpha = 1f - settleAlpha
            if (beadAlpha <= 0f || markerScale <= 0f) return@Canvas
            val tipRad = tipDeg * PI.toFloat() / 180f
            drawCircle(
                color = activeColor.copy(alpha = beadAlpha),
                radius = trackPx * 0.7f * markerScale,
                center = Offset(
                    center.x + cos(tipRad) * ringRPx,
                    center.y + sin(tipRad) * ringRPx,
                ),
            )
        }
    }
}

/**
 * Sweep-gradient color stops (absolute canvas angle, 0..360, first and last
 * matching for a seamless wrap) giving [color] full opacity at [tipDeg] and
 * fading to 10% opacity one full turn behind it, in the direction opposite
 * travel ([clockwise]).
 */
private fun trailFadeColors(color: Color, tipDeg: Float, clockwise: Boolean): List<Color> {
    val stops = 96
    return List(stops + 1) { i ->
        val absDeg = i / stops.toFloat() * 360f
        val back = if (clockwise) {
            (((tipDeg - absDeg) % 360f) + 360f) % 360f
        } else {
            (((absDeg - tipDeg) % 360f) + 360f) % 360f
        }
        color.copy(alpha = (1f - 0.9f * (back / 360f)).coerceIn(0.1f, 1f))
    }
}

/** One player dot on the ring. Split out so untouched dots skip recomposition. */
@Composable
private fun SeatDot(
    color: Color,
    sizeDp: Dp,
    alpha: Float,
    offsetPx: IntOffset,
    onTap: () -> Unit,
) {
    Box(
        modifier = Modifier
            .offset { offsetPx }
            .size(sizeDp)
            .alpha(alpha)
            .clip(CircleShape)
            .background(color)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
            )
    )
}

/**
 * Score label near the screen edge. Square box (rotation-proof bounds);
 * tap rotates it 90° to face its player. Split out so only the active
 * player's label recomposes while a swipe is in progress.
 */
@Composable
private fun SeatScore(
    player: Player,
    text: String,
    showName: Boolean,
    scoreSp: Float,
    boxDp: Dp,
    dimmed: Boolean,
    offsetPx: IntOffset,
    onTap: () -> Unit,
) {
    val color = Color(player.color)
    val digits = text.filter { it.isDigit() }.length
    val size = (scoreSp - (digits - 1).coerceAtLeast(0) * 2.5f).coerceAtLeast(14f)
    Box(
        modifier = Modifier
            .offset { offsetPx }
            .size(boxDp)
            .alpha(if (dimmed) 0.4f else 1f)
            .rotate(RotationUtils.degrees(player.rotation))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (showName) {
                Text(
                    text = player.name,
                    fontSize = (size * 0.24f).coerceAtLeast(10f).sp,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                text = text,
                fontSize = size.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}
