package com.scoreorbit.android.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.scoreorbit.android.model.Game
import com.scoreorbit.android.model.Player
import com.scoreorbit.android.model.Team
import com.scoreorbit.android.model.WinMetric
import com.scoreorbit.android.util.RotationUtils
import com.scoreorbit.android.viewmodel.AppScreen
import com.scoreorbit.android.viewmodel.ScoreViewModel
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Geometry snapshot the seat-swap tracker reads through. The label
 * detectors keep running across live swaps without restarting, so they
 * may only read geometry through this stable handle — never captured
 * arrays, which go stale the moment the roster changes.
 */
private data class BoardGeo(
    val players: List<Player>,
    val dots: DoubleArray,
    val centers: Array<Offset?>,
    val boxes: FloatArray,
    /** Label widths (rows are wider than tall); squares repeat [boxes]. */
    val widths: FloatArray,
    /** Touch slop in px for seat hit-testing. */
    val pad: Float,
    val zones: List<Zone>,
)

/**
 * Seat-drag tracking that outlives touch-detector restarts. Detectors
 * restart whenever their keys change — including mid-gesture on live
 * swaps — which wipes block-local finger state and reads as a teleport.
 * Everything here lives in composition state instead, so a restart is a
 * no-op: the next touch event picks up exactly where tracking left off.
 */
private class SeatDragState {
    var draggedId by mutableStateOf<String?>(null)
        private set
    var finger by mutableStateOf(Offset.Zero)
        private set
    /** Foreign zone armed for a team join on drop, with its team (null = solos). */
    var dropArmed by mutableStateOf(false)
        private set
    var dropTeamId: String? by mutableStateOf<String?>(null)
        private set
    private var grab = Offset.Zero
    private var coolPartner = ""
    private var coolUntil = 0L

    /** Finger minus grab point: the carried label's exact translation. */
    val carry: Offset get() = finger - grab

    /** Claim the gesture for [id] starting at label center [at]. */
    fun claim(id: String, at: Offset): Boolean {
        if (draggedId != null) return false
        draggedId = id
        finger = at
        grab = at
        coolPartner = ""
        dropArmed = false
        dropTeamId = null
        return true
    }

    /**
     * Advance the finger. Inside a foreign zone it arms a team join for
     * the drop (no seat trading there); inside the dragged player's own
     * zone, teammates trade seats live. Solo games have one zone, so
     * everything trades as before.
     */
    fun move(
        amount: Offset,
        geo: BoardGeo,
        onSwap: (String) -> Unit,
        onTick: () -> Unit,
    ) {
        val dragged = draggedId ?: return
        finger += amount
        val me = geo.players.firstOrNull { it.id == dragged } ?: return
        val zone = geo.zones.firstOrNull { z ->
            finger.x in z.rect.l..z.rect.r && finger.y in z.rect.t..z.rect.b
        }
        if (zone != null && zone.team?.id != me.teamId) {
            if (!dropArmed || dropTeamId != zone.team?.id) onTick()
            dropArmed = true
            dropTeamId = zone.team?.id
            return
        }
        dropArmed = false
        dropTeamId = null
        val idx = geo.players.indexOfFirst { it.id == dragged }
        if (idx < 0) return
        val now = System.nanoTime()
        geo.players.forEachIndexed { j, p ->
            if (p.id == dragged || p.teamId != me.teamId) return@forEachIndexed
            val oc = geo.centers[j] ?: return@forEachIndexed
            // Ellipse test: rows are wide and short, squares are round —
            // one test covers both, so a finger anywhere over a label hits.
            val rx = (geo.widths.getOrElse(idx) { 0f } + geo.widths.getOrElse(j) { 0f }) / 2f + geo.pad
            val ry = (geo.boxes.getOrElse(idx) { 0f } + geo.boxes.getOrElse(j) { 0f }) / 2f + geo.pad
            if (rx <= 0f || ry <= 0f) return@forEachIndexed
            val ex = (finger.x - oc.x) / rx
            val ey = (finger.y - oc.y) / ry
            if (ex * ex + ey * ey < 1f) {
                if (p.id == coolPartner && now < coolUntil) return@forEachIndexed
                onSwap(p.id)
                // Rebase onto the new seat so the visual stays glued to
                // the finger instead of jumping.
                grab = oc
                coolPartner = p.id
                coolUntil = now + 400_000_000L
                onTick()
                return
            }
        }
    }

    fun release() {
        draggedId = null
        dropArmed = false
        dropTeamId = null
    }
}

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

/**
 * Dial angles snapped to an even spread while staying as close as possible
 * to each score: every dot starts parked on the ray from the dial center
 * toward its own score label, then the whole set snaps to evenly spaced
 * slots — trying every rotation and keeping the least total movement — so
 * crowded labels share the compromise instead of piling dots on each other.
 */
private fun computeDotAngles(
    labelPositions: Array<Offset?>,
    cx: Float,
    cy: Float,
    ringR: Float,
    dotD: Float,
): DoubleArray {
    val n = labelPositions.size
    // Candidate: ray from center through each label's final position.
    val cand = DoubleArray(n) { i ->
        val p = labelPositions[i]!!
        var a = atan2((p.y - cy).toDouble(), (p.x - cx).toDouble())
        if (a < 0) a += 2 * PI
        a
    }
    if (n < 2) return cand
    // Minimum center-to-center separation (chord) so dots keep a small gap.
    val minSep = 2.0 * asin(min(1.0, (dotD * 1.1 / 2.0) / ringR.toDouble()))
    // Dots are sized from their angular share, so a full even spread always
    // fits; if it somehow couldn't, fall back to even spacing to keep the
    // dial usable.
    if (minSep * n > 2 * PI) {
        return DoubleArray(n) { -PI / 2 + it * 2 * PI / n }
    }
    // Unwrap candidates in circular order, duplicated with +2PI so every
    // rotation is a contiguous window.
    val order = (0 until n).sortedBy { cand[it] }
    val ext = DoubleArray(2 * n)
    for (k in 0 until 2 * n) {
        var target = cand[order[k % n]]
        if (k > 0) {
            while (target < ext[k - 1]) target += 2 * PI
        }
        ext[k] = target
    }
    // Even slots; try every rotation, keep the least total movement.
    val step = 2 * PI / n
    var bestStart = 0
    var bestBase = 0.0
    var bestCost = Double.MAX_VALUE
    for (s in 0 until n) {
        var base = 0.0
        for (k in 0 until n) base += ext[s + k] - k * step
        base /= n
        var cost = 0.0
        for (k in 0 until n) cost += abs(ext[s + k] - (base + k * step))
        if (cost < bestCost) {
            bestCost = cost
            bestStart = s
            bestBase = base
        }
    }
    val result = DoubleArray(n)
    for (k in 0 until n) {
        var a = (bestBase + k * step) % (2 * PI)
        if (a < 0) a += 2 * PI
        result[order[(bestStart + k) % n]] = a
    }
    return result
}

private fun nearestSeat(point: Offset, center: Offset, dotAngles: DoubleArray): Int {
    val a = atan2((point.y - center.y).toDouble(), (point.x - center.x).toDouble())
    var best = 0
    var bestD = Double.MAX_VALUE
    for (i in dotAngles.indices) {
        var d = abs(a - dotAngles[i]) % (2 * PI)
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

/**
 * One board zone in team mode: a quadrant owned by a team (tinted, with a
 * total chip in its outer corner) or the solo zone (untinted, no chip).
 * Member positions are zone-local; add the zone origin for board coords.
 */
private data class ZoneRect(val l: Float, val t: Float, val r: Float, val b: Float)

private data class ZoneMember(
    val playerIndex: Int,
    /** Center, zone-local. */
    val local: Offset,
    /** Height in px (squares: edge; rows: strip height). */
    val boxPx: Float,
    /** Width in px (squares repeat [boxPx]; rows span wider). */
    val rowW: Float,
)

private data class Zone(
    val rect: ZoneRect,
    val team: Team?,
    val corner: Alignment,
    val members: List<ZoneMember>,
    val rowMode: Boolean,
)

/**
 * Row layout for packed zones: short full strips (name + score side by
 * side) instead of shrunken squares, so names stay big and readable.
 * Rows live only in the dial-safe band — above the ring cutout in top
 * zones, below it in bottom ones — and stack outward from the dial.
 * Full-width zones (halves) use two columns; side zones use one.
 */
private fun layoutRowMembers(
    memberIdx: List<Int>,
    rect: ZoneRect,
    zoneW: Float,
    zoneH: Float,
    wPx: Float,
    hPx: Float,
    gapY: Float,
    padX: Float,
    chipReserve: Float,
    wide: Boolean,
    cutR: Float,
    density: Density,
): List<ZoneMember> {
    val cxD = wPx / 2f
    val cyD = hPx / 2f
    val isTop = rect.b <= hPx / 2f + 1f
    // Vertical band that clears both the dial cutout and the chip corner.
    val bandTop = (if (isTop) rect.t + chipReserve else maxOf(rect.t, cyD + cutR))
    val bandBot = (if (isTop) minOf(rect.b, cyD - cutR) else rect.b - chipReserve)
    val bandH = (bandBot - bandTop).coerceAtLeast(0f)
    // Columns: halves split left/right, side zones keep one full column.
    val colCount = if (wide) 2 else 1
    val colGap = if (wide) gapY * 1.5f else 0f
    val colW = ((zoneW - 2f * padX - (colCount - 1) * colGap) / colCount).coerceAtLeast(0f)
    val perCol = (memberIdx.size + colCount - 1) / colCount
    val rowH = ((bandH - (perCol - 1) * gapY) / perCol.coerceAtLeast(1))
        .coerceIn(with(density) { 34.dp.toPx() }, with(density) { 64.dp.toPx() })
    // Evenly spread what fits; overflow (absurd counts) packs downward.
    val totalH = perCol * rowH + (perCol - 1) * gapY
    val startY = bandTop + ((bandH - totalH) / 2f).coerceAtLeast(0f) + rowH / 2f
    val yAt = { k: Int -> startY + k * (rowH + gapY) }
    // Left column takes the first half in roster order, right the rest.
    val split = (memberIdx.size + 1) / 2
    // Breathing room from the team chip: top zones settle downward,
    // bottom zones upward.
    val nudge = with(density) { 4.dp.toPx() } * if (isTop) 1f else -1f
    return memberIdx.mapIndexed { k, pi ->
        // Column of this member: halves fill left column first.
        val c = if (!wide) 0 else if (k < split) 0 else 1
        val row = if (!wide) k else if (k < split) k else k - split
        val left = if (!wide) rect.l + padX
        else if (c == 0) rect.l + padX
        else rect.r - padX - colW
        val width = if (wide) colW else zoneW - 2f * padX
        val centerX = left + width / 2f
        val centerY = (yAt(row) + nudge).coerceIn(rect.t + rowH / 2f, rect.b - rowH / 2f)
        ZoneMember(pi, Offset(centerX - rect.l, centerY - rect.t), rowH, width)
    }
}

/**
 * Quadrant layout for team games. Non-empty teams take the quadrants in
 * order (top-left, top-right, bottom-left, bottom-right), solos share one
 * trailing zone. Members form a centered grid fitted to their zone; more
 * than four groups share the last zone instead of breaking the layout.
 * Pure geometry — scoring state stays per player, untouched.
 */
private fun computeZones(
    players: List<Player>,
    teams: List<Team>,
    wPx: Float,
    hPx: Float,
    labelBoxPx: Float,
    ringR: Float,
    dotD: Float,
    density: Density,
): List<Zone> {
    val teamGroups = teams.filter { t -> players.any { it.teamId == t.id } }
    val soloIdx = players.mapIndexedNotNull { i, p -> if (p.teamId == null) i else null }
    val groups = ArrayDeque<Pair<Team?, List<Int>>>()
    teamGroups.forEach { t ->
        groups.add(t to players.mapIndexedNotNull { i, p -> if (p.teamId == t.id) i else null })
    }
    if (soloIdx.isNotEmpty()) groups.add(null to soloIdx)
    while (groups.size > 4) {
        val overflow = groups.removeLast()
        val last = groups.removeLast()
        // Overflow shares the last zone: members appended, grid refit.
        groups.add(last.first to (last.second + overflow.second))
    }
    val corners = listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart, Alignment.BottomEnd)
    val gapPx = with(density) { 12.dp.toPx() }
    val insetPx = with(density) { 4.dp.toPx() }
    // A lone group owns the whole board: it gets the legacy edge layout
    // (which already handles any count) under a full-board tint, so one
    // team never crams into a single quadrant.
    if (groups.size == 1) {
        val (team, _) = groups[0]
        return listOf(
            Zone(
                ZoneRect(insetPx, insetPx, wPx - insetPx, hPx - insetPx),
                team,
                Alignment.TopStart,
                emptyList(),
                false,
            )
        )
    }
    return groups.mapIndexed { zi, (team, memberIdx) ->
        // Two groups split the board into top/bottom halves, first group
        // on top; three or four fall back to quadrants.
        val halves = groups.size == 2
        val rect = if (halves) {
            if (zi == 0) ZoneRect(insetPx, insetPx, wPx - insetPx, hPx / 2f - insetPx)
            else ZoneRect(insetPx, hPx / 2f + insetPx, wPx - insetPx, hPx - insetPx)
        } else when (zi) {
                0 -> ZoneRect(insetPx, insetPx, wPx / 2f - insetPx, hPx / 2f - insetPx)
                1 -> ZoneRect(wPx / 2f + insetPx, insetPx, wPx - insetPx, hPx / 2f - insetPx)
                2 -> ZoneRect(insetPx, hPx / 2f + insetPx, wPx / 2f - insetPx, hPx - insetPx)
                else -> ZoneRect(wPx / 2f + insetPx, hPx / 2f + insetPx, wPx - insetPx, hPx - insetPx)
            }
            val m = memberIdx.size
            // Wide zones (halves) fill across; square ones stay compact.
            // Three across in a single row beats a 2+1 stagger.
            val zoneW = rect.r - rect.l
            val zoneH = rect.b - rect.t
            val wide = zoneW > zoneH * 1.25f
            val cols = when {
                m <= 1 -> 1
                m == 2 -> 2
                wide -> min(3, m)
                m == 3 -> 3
                m <= 4 -> 2
                else -> 3
            }
            val rows = (m + cols - 1) / cols
            val pitch = labelBoxPx + gapPx
            val fit = min(
                1f,
                min(
                    (zoneW - gapPx) / (cols * pitch),
                    (zoneH - gapPx) / (rows * pitch),
                ),
            ).coerceAtLeast(0.4f)
            val members: List<ZoneMember>
            val rowMode = fit < 0.8f
            if (rowMode) {
                members = layoutRowMembers(
                    memberIdx = memberIdx,
                    rect = rect,
                    zoneW = zoneW,
                    zoneH = zoneH,
                    wPx = wPx,
                    hPx = hPx,
                    gapY = with(density) { 6.dp.toPx() },
                    padX = with(density) { 4.dp.toPx() },
                    chipReserve = if (team != null) with(density) { 56.dp.toPx() } else 0f,
                    wide = wide,
                    // Matches the backdrop hole below: ring outer edge
                    // (ring + half the dot) plus a small margin.
                    cutR = ringR + dotD * 0.5f + with(density) { 8.dp.toPx() },
                    density = density,
                )
            } else {
                val box = labelBoxPx * fit
                val step = pitch * fit
                // Grid center pushed outward from the dial so member labels
                // clear the ring, then clamped so the fitted grid stays inside
                // its zone.
                val rawCx = (rect.l + rect.r) / 2f
                val rawCy = (rect.t + rect.b) / 2f
                val dx = rawCx - wPx / 2f
                val dy = rawCy - hPx / 2f
                val len = sqrt(dx * dx + dy * dy)
                val push = min(zoneW, zoneH) * 0.18f
                val wantCx = if (len > 0f) rawCx + dx / len * push else rawCx
                val wantCy = if (len > 0f) rawCy + dy / len * push else rawCy
                val hw = ((cols - 1) * step + box) / 2f
                val hh = ((rows - 1) * step + box) / 2f
                val gcX = if (hw * 2f >= zoneW) rawCx else wantCx.coerceIn(rect.l + hw, rect.r - hw)
                val gcY = if (hh * 2f >= zoneH) rawCy else wantCy.coerceIn(rect.t + hh, rect.b - hh)
                val cx = gcX - rect.l
                val cy = gcY - rect.t
                members = memberIdx.mapIndexed { k, pi ->
                    val gy = k / cols
                    val rowLen = min(cols, m - gy * cols)
                    val gx = k % cols
                    val x = cx + (gx - (rowLen - 1) / 2f) * step
                    val y = cy + (gy - (rows - 1) / 2f) * step
                    // Same hard guarantee as the band grid: grid cells near the
                    // dial's inner corner get pushed back out radially.
                    val pushed = pushOutsideRing(
                        Offset(rect.l + x, rect.t + y),
                        wPx / 2f, hPx / 2f,
                        ringR + dotD / 2f + box / 2f + with(density) { 12.dp.toPx() },
                    )
                    ZoneMember(pi, Offset(pushed.x - rect.l, pushed.y - rect.t), box, box)
                }
            }
            Zone(
                rect,
                team,
                if (halves) {
                    if (zi == 0) Alignment.TopStart else Alignment.BottomStart
                } else {
                    corners[zi]
                },
                members,
                rowMode,
            )
        }
}

/**
 * Hard guarantee: no label may touch the ring. The guard is box-aware —
 * the label's ring-facing edge (half the box plus a gap) must clear the
 * track, not just its center — because soft clearances size the layout
 * while compression and odd aspect ratios can still shove a row into the
 * dial. Intruders get pushed back out along their own ray. Runs before
 * the on-screen clamp.
 */
private fun pushOutsideRing(p: Offset, cx: Float, cy: Float, minDist: Float): Offset {
    val dx = p.x - cx
    val dy = p.y - cy
    val d = sqrt(dx * dx + dy * dy)
    return if (d < minDist && d > 1e-3f) Offset(cx + dx / d * minDist, cy + dy / d * minDist) else p
}

/**
 * Edge-label positions for every seat. 1-3 players use the seatAngle-driven
 * layout (anchors at their natural rotary start point; side players stack
 * off a same-half anchor, or off their own ring-edge spot when there isn't
 * one). 4+ players use the player-order band grid. Pure layout math: it only
 * depends on the board geometry, never on gesture state.
 */
private fun computeLabelPositions(
    n: Int,
    wPx: Float,
    hPx: Float,
    cx: Float,
    cy: Float,
    ringR: Float,
    dotD: Float,
    labelBoxPx: Float,
    edgeMarginPx: Float,
    density: Density,
): Array<Offset?> {
    val center = Offset(cx, cy)
    val positions = arrayOfNulls<Offset>(n)
    val stackOffset = labelBoxPx + with(density) { 12.dp.toPx() }
    // Minimum breathing room between a label and the ring/dots so
    // crowded boards never read as clipped into the dial. Small boards
    // get generous air; crowded ones trade some of it so the bands fit
    // without shoving rows into the ring.
    val labelClearPx = with(density) {
        when {
            n <= 4 -> 40.dp.toPx()
            n <= 6 -> 32.dp.toPx()
            else -> 24.dp.toPx()
        }
    }
    if (n >= 4) {
        // Fixed 3-column band grid in player order: the first
        // half of the players fills the top band left to right,
        // top to bottom (P1 top-left, P2 top-middle, ...), the
        // rest fill the bottom band the same way. Short rows are
        // centered so columns stay aligned across rows. Wide
        // cells let scores fill the free space instead of
        // squeezing into per-seat columns.
        val nearD = ringR + dotD / 2f + labelClearPx
        val rowPitch = labelBoxPx + with(density) { 12.dp.toPx() }
        val xLo = edgeMarginPx + labelBoxPx / 2f
        val xHi = wPx - edgeMarginPx - labelBoxPx / 2f
        val anchors = floatArrayOf(xLo, (xLo + xHi) / 2f, xHi)
        val topCount = (n + 1) / 2
        for ((band, sign) in listOf(
            (0 until topCount).toList() to -1f,
            (topCount until n).toList() to 1f,
        )) {
            // Fit each band's own rows: a band with fewer rows
            // keeps full clearance instead of inheriting the
            // compression of a more crowded band.
            val rows = band.chunked(3)
            val vRoom = (min(cy, hPx - cy) - edgeMarginPx - labelBoxPx / 2f)
                .coerceAtLeast(0f)
            val vWant = nearD + (rows.size - 1) * rowPitch + labelBoxPx / 2f
            val vFit = if (vWant > vRoom && vWant > 0f) {
                (vRoom / vWant).coerceIn(0.2f, 1f)
            } else 1f
            rows.forEachIndexed { r, row ->
                val xs = when (row.size) {
                    3 -> anchors
                    2 -> floatArrayOf(anchors[0], anchors[2])
                    else -> floatArrayOf(anchors[1])
                }
                // Top band fills outward (first chunk farthest)
                // so P1 lands top-left; bottom band fills
                // downward in the same reading order.
                val rr = if (sign < 0f) rows.size - 1 - r else r
                row.forEachIndexed { c, i ->
                    val d = (nearD + rr * rowPitch + labelBoxPx / 2f) * vFit
                    positions[i] = pushOutsideRing(
                        Offset(xs[c], cy + sign * d),
                        cx, cy,
                        ringR + dotD / 2f + labelBoxPx / 2f + with(density) { 12.dp.toPx() },
                    )
                }
            }
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
            positions[i] = Offset(cx + dirX * dist, cy + dirY * dist)
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
            anchorPosOf[i] = positions[anchorIdx]
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
            positions[i] = Offset(
                anchorPos.x,
                if (aboveOf[i]) anchorPos.y - totalOffset else anchorPos.y + totalOffset
            )
        }
    }
    // Final guard for every layout above: no label may leave the
    // screen. Crowded boards overlap slightly instead of
    // clipping cut-off text at the edges.
    for (i in 0 until n) {
        val p = positions[i] ?: continue
        val loX = labelBoxPx / 2f + edgeMarginPx
        val hiX = wPx - labelBoxPx / 2f - edgeMarginPx
        val loY = labelBoxPx / 2f + edgeMarginPx
        val hiY = hPx - labelBoxPx / 2f - edgeMarginPx
        positions[i] = Offset(
            x = if (hiX <= loX) wPx / 2f else p.x.coerceIn(loX, hiX),
            y = if (hiY <= loY) hPx / 2f else p.y.coerceIn(loY, hiY),
        )
    }
    return positions
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoreboardScreen(game: Game, viewModel: ScoreViewModel) {
    val ctx = LocalContext.current
    var latestGame by remember { mutableStateOf(game) }
    latestGame = game

    var activeId by remember(game.id) { mutableStateOf<String?>(null) }
    var pending by remember(game.id) { mutableIntStateOf(0) }
    // Seat-swap drag: long-press a score to pick it up, carry it onto
    // another seat, and the two trade places live (like roster reorder).
    // Swaps commit the moment seats cross. The holder below is the single
    // source of truth for who is carried; the latch + clock let either
    // teardown recover fully under any ordering.
    var swapEngaged by remember(game.id) { mutableStateOf(false) }
    var swapEndTime by remember(game.id) { mutableStateOf(0L) }
    // Restart-proof drag tracking (see SeatDragState).
    val seatDrag = remember(game.id) { SeatDragState() }
    // Seat fingerprint for the dial detector keys (order changes it,
    // rotations don't — see the pointerInput below). Team assignment is
    // part of the print too: a team move keeps the order but re-parks
    // every dot, so the grab map must refresh for that as well.
    val seatIds = remember(game.players) { game.players.map { it.id } }
    val seatTeams = remember(game.players) { game.players.map { it.teamId } }
    var accRadians by remember(game.id) { mutableFloatStateOf(0f) }
    var lastAngle by remember { mutableFloatStateOf(Float.NaN) }
    // Last committed action, flashed in the middle of the ring as name +
    // final delta, fading out on its own after ~2s.
    var lastFlash by remember(game.id) { mutableStateOf<Triple<Int, Int, String>?>(null) }
    var flashAlpha by remember(game.id) { mutableFloatStateOf(0f) }
    // Settle fade 0->1 after the rewind: bead fades out while the hidden
    // dots fade back in, so nothing pops.
    var settle by remember(game.id) { mutableFloatStateOf(0f) }
    // Marker grow 0->1 on grab (only when enlargement is on).
    var markerScale by remember(game.id) { mutableFloatStateOf(0f) }
    var markerJob by remember(game.id) { mutableStateOf<Job?>(null) }
    var flashJob by remember(game.id) { mutableStateOf<Job?>(null) }
    // In-flight "spring back" animation that unwinds the dial after release.
    val scope = rememberCoroutineScope()
    var springJob by remember(game.id) { mutableStateOf<Job?>(null) }

    val turn = game.rotationPoints.coerceAtLeast(1)
    // Committed totals are O(ledger) each: compute once per ledger change,
    // not once per player on every drag frame.
    val totals = remember(game.entries, game.players) { game.scoresMap() }
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
            // One shared dissolve so dots, bead and trail move together:
            // the hidden dots fade back in over the same window that the
            // bead and the sweep color fade out. Runs after every release
            // — even tiny flicks — so nothing ever pops, and nothing ever
            // finishes before anything else.
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = tween(durationMillis = 990),
            ) { value, _ -> settle = value }
            accRadians = 0f
            settle = 0f
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
                    if (game.name.isNotBlank()) {
                        Text(
                            game.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.go(AppScreen.ScoreHistory) }) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = "Score history",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                        IconButton(onClick = { viewModel.go(AppScreen.Leaderboard) }) {
                            Icon(
                                Icons.Default.Leaderboard,
                                contentDescription = "Leaderboard",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.go(AppScreen.Settings) }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
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

            // Bottom strip reserved for system gesture navigation: the whole
            // layout lives above it, and a transparent overlay (below)
            // swallows every touch inside it, so a swipe-up-home never grabs
            // the dial or taps a label by accident.
            val deadZoneFrac = 0.08f
            val layoutH = hPx * (1f - deadZoneFrac)
            val cx = wPx / 2f
            val cy = layoutH / 2f
            val center = Offset(cx, cy)
            val n = game.players.size
            val minDim = min(wPx, hPx)
            fun dp(px: Float): Dp = with(density) { px.toDp() }

            // Ring sized for the narrow side; dots sit on it. Both run 15% slimmed
            // down from raw scale so the dial doesn't crowd the labels.
            // High counts get a slightly smaller dial so the label bands
            // keep generous clearance instead of crowding the ring.
            val ringR = minDim * when {
                n <= 6 -> 0.34f
                n <= 8 -> 0.32f
                else -> 0.30f
            }
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
            // Small counts use large boxes; 4+ players use the band grid
            // below, so the box comes straight from the column width: as
            // large as it can be while still fitting three across.
            val edgeMarginPx = with(density) { 8.dp.toPx() }
            val labelBoxPx = when {
                n <= 3 -> with(density) { 140.dp.toPx() }
                n <= 6 -> with(density) { 100.dp.toPx() }
                else -> {
                    // Band grid uses 3 fixed columns: size the box from the
                    // column width so scores fill the free space instead of
                    // squeezing into per-seat columns. Crowded boards cap
                    // smaller so rows keep air between them.
                    val gap = with(density) { 4.dp.toPx() }
                    ((wPx - 2 * edgeMarginPx - 2 * gap) / 3f)
                        .coerceIn(with(density) { 64.dp.toPx() }, with(density) { 84.dp.toPx() })
                }
            }
            val scoreSp = (labelBoxPx * 0.44f / density.density).coerceIn(20f, 64f)

            // Team mode: quadrants own the labels and each team gets a total
            // chip. Solo-only games keep the legacy edge layout exactly.
            // A lone group does too (full-board tint, edge labels) so one
            // team never crams into a single quadrant.
            val zoned = game.players.any { it.teamId != null }
            val zones: List<Zone> = if (zoned) {
                remember(game.players, game.teams, wPx, layoutH, labelBoxPx, ringR, dotD, density) {
                    computeZones(game.players, game.teams, wPx, layoutH, labelBoxPx, ringR, dotD, density)
                }
            } else emptyList()
            val singleFull = zoned && zones.size == 1 && zones.all { it.members.isEmpty() }
            // Scores pushed out to the screen edges (memoized: pure layout
            // math, independent of gesture state). 1-3 players use the
            // seatAngle-driven layout (anchors at their natural rotary
            // start point; side players stack off a same-half anchor, or
            // off their own ring-edge spot when there isn't one). 4+
            // players use the player-order band grid instead. Team games
            // read positions back out of the zone grids.
            val labelPositions: Array<Offset?> = if (zoned && !singleFull) {
                remember(zones) {
                    arrayOfNulls<Offset>(n).also { arr ->
                        zones.forEach { z ->
                            z.members.forEach { m ->
                                arr[m.playerIndex] = Offset(z.rect.l + m.local.x, z.rect.t + m.local.y)
                            }
                        }
                    }
                }
            } else {
                remember(
                    n, wPx, layoutH, ringR, dotD, labelBoxPx, edgeMarginPx, density,
                ) {
                    computeLabelPositions(n, wPx, layoutH, cx, cy, ringR, dotD, labelBoxPx, edgeMarginPx, density)
                }
            }
            // Per-player label box: uniform in legacy mode, fitted per zone
            // member in team mode (plus matching widths for rows).
            val memberBoxPx = if (zoned && !singleFull) {
                remember(zones) {
                    FloatArray(n) { labelBoxPx }.also { arr ->
                        zones.forEach { z ->
                            z.members.forEach { m -> arr[m.playerIndex] = m.boxPx }
                        }
                    }
                }
            } else {
                remember(labelBoxPx, n) { FloatArray(n) { labelBoxPx } }
            }
            val memberWidthPx = if (zoned && !singleFull) {
                remember(zones) {
                    FloatArray(n) { labelBoxPx }.also { arr ->
                        zones.forEach { z ->
                            z.members.forEach { m -> arr[m.playerIndex] = m.rowW }
                        }
                    }
                }
            } else {
                remember(labelBoxPx, n) { FloatArray(n) { labelBoxPx } }
            }
            fun memberScoreSp(boxPx: Float): Float =
                (boxPx * 0.44f / density.density).coerceIn(14f, 64f)
            val teamTotals = remember(game.entries, game.players, game.teams) {
                game.teamScoresMap()
            }
            // Podium ranks follow the win metric. Team games badge each team
            // plus any solo players (ranked among solos).
            val lowestWins = game.winMetric == WinMetric.LOWEST
            val teamRanks = remember(teamTotals, lowestWins) {
                podiumRanks(teamTotals.values.toList(), lowestWins)
                    .let { r -> teamTotals.keys.zip(r).toMap() }
            }
            val playerRanks = remember(totals, game.players, zoned, lowestWins) {
                // Team members carry no badge; solos in a team game are
                // ranked among themselves, as on the leaderboard.
                val ranked = game.players.withIndex().filter { !zoned || it.value.teamId == null }
                val r = podiumRanks(ranked.map { totals[it.value.id] ?: 0 }, lowestWins)
                IntArray(game.players.size).also { out ->
                    ranked.forEachIndexed { k, iv -> out[iv.index] = r[k] }
                }
            }
            // Dial angles, one dot parked near its own score then snapped
            // to even spacing (memoized likewise).
            val dotAngles: DoubleArray = remember(labelPositions, cx, cy, ringR, dotD) {
                computeDotAngles(labelPositions, cx, cy, ringR, dotD)
            }
            // Geometry handle for the seat-swap trackers. Updated every
            // composition, read fresh on every touch event through the
            // stable object (the conditional write converges immediately).
            val boardGeoState = remember { mutableStateOf(BoardGeo(game.players, dotAngles, labelPositions, memberBoxPx, memberWidthPx, with(density) { 4.dp.toPx() }, zones)) }
            val freshGeo = remember(game.players, dotAngles, labelPositions, memberBoxPx, memberWidthPx, zones) {
                BoardGeo(game.players, dotAngles, labelPositions, memberBoxPx, memberWidthPx, with(density) { 4.dp.toPx() }, zones)
            }
            if (boardGeoState.value !== freshGeo) boardGeoState.value = freshGeo

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Keyed on the seat fingerprint: order changes restart
                    // the tracker (refreshing the captured seat map), while
                    // rotations don't (a second-finger tap mid-spin must not
                    // kill the dial). Restarts only ever happen between
                    // gestures — roster changes can't occur mid-dial-spin,
                    // and swaps own their gestures.
                    .pointerInput(game.id, seatIds, seatTeams, game.rotationPoints) {
                        // Per-gesture witnesses: did THIS gesture see swap
                        // activity, and what was the swap clock when it
                        // started? Combined with the latch they identify a
                        // swap ending under any teardown ordering.
                        var sawSwap = false
                        var startMark = 0L
                        detectDragGestures(
                            onDragStart = { offset ->
                                val g = latestGame
                                if (g.players.isEmpty()) return@detectDragGestures
                                sawSwap = false
                                startMark = swapEndTime
                                // Grabbing the dial again mid spring-back cancels the
                                // return animation instead of fighting it for control
                                // of accRadians.
                                resetGesture()
                                val seat = nearestSeat(offset, center, dotAngles)
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
                            onDragEnd = {
                                // Reset unless this was a pure dial gesture.
                                // Any one of these independently proves a
                                // swap was involved, so no teardown ordering
                                // can misroute into a score commit.
                                if (sawSwap || swapEngaged || swapEndTime != startMark) {
                                    resetGesture()
                                    swapEngaged = false
                                } else {
                                    commitGesture()
                                }
                            },
                            // A cancelled gesture (incoming call, etc.) still
                            // keeps completed points instead of dropping them.
                            onDragCancel = {
                                if (sawSwap || swapEngaged || swapEndTime != startMark) {
                                    resetGesture()
                                    swapEngaged = false
                                } else {
                                    commitGesture()
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                // A seat-swap owns this gesture: the dial
                                // stays out of it entirely.
                                if (seatDrag.draggedId != null) {
                                    sawSwap = true
                                    return@detectDragGestures
                                }
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
                                    if (latestGame.hapticsEnabled) {
                                        buzz(ctx, 18, latestGame.hapticStrength)
                                    }
                                }
                            }
                        )
                    }
            ) {
                // Team zones, pass one: tinted backgrounds (dial cut out)
                // plus total chips — everything below the ring, dots and
                // labels, which render in the existing loops further down.
                if (zoned) {
                    zones.forEach { zone ->
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(zone.rect.l.toInt(), zone.rect.t.toInt()) }
                                .size(
                                    with(density) { (zone.rect.r - zone.rect.l).toDp() },
                                    with(density) { (zone.rect.b - zone.rect.t).toDp() },
                                )
                        ) {
                            ZoneBackdrop(
                                zone = zone,
                                ringR = ringR,
                                dotD = dotD,
                                cx = cx,
                                cy = cy,
                                density = density,
                                targeted = seatDrag.dropArmed &&
                                    zone.team?.id == seatDrag.dropTeamId,
                                total = teamTotals[zone.team?.id] ?: 0,
                                rank = teamRanks[zone.team?.id] ?: 0,
                            )
                        }
                    }
                }
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
                    trailAlpha = 1f - settle,
                    // Arc trails from the player's dot along the drag. Not
                    // clamped to one lap: RingDial itself turns anything
                    // beyond 360° into a stacked, full-circle fade instead of
                    // truncating the visual at the first turn.
                    arcStartDeg = activeIndex.takeIf { it >= 0 }
                        ?.let { (dotAngles[it] * 180.0 / PI).toFloat() } ?: -90f,
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
                // Readout anchored on the ring center (cx, cy) — not the box
                // center, which sits lower now that the layout reserves the
                // bottom strip for gesture navigation.
                var readoutSize by remember { mutableStateOf(IntSize.Zero) }
                val readoutAnchor = Modifier
                    .align(Alignment.TopStart)
                    .onSizeChanged { readoutSize = it }
                    .offset {
                        IntOffset(
                            x = (cx - readoutSize.width / 2f).roundToInt(),
                            y = (cy - readoutSize.height / 2f).roundToInt(),
                        )
                    }
                if (showLive && livePlayer != null) {
                    val c = Color(livePlayer.color)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = readoutAnchor,
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
                        modifier = readoutAnchor
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

                // Race to the target: the leader at or past it ends the game
                // with confetti. Dismissing ("keep playing") snoozes the
                // celebration until the next score lands; a new game resets
                // it.
                val target = game.targetScore
                val boardOrder = remember(totals, game.players, game.winMetric) {
                    game.players.sortedWith(
                        if (game.winMetric == WinMetric.LOWEST) compareBy { totals[it.id] ?: 0 }
                        else compareByDescending { totals[it.id] ?: 0 }
                    )
                }
                val champion = if (target != null && boardOrder.isNotEmpty() &&
                    (totals[boardOrder.first().id] ?: 0) >= target
                ) boardOrder.first() else null
                // Celebration waits out the score count-up (1200ms) plus a
                // beat, so the dialog never covers the winning number
                // landing. Dismissing ("keep playing") ends celebrations for
                // this game entirely; a new game resets it.
                var celebrationDismissed by remember(game.id) { mutableStateOf(false) }
                var winnerReady by remember(game.id) { mutableStateOf(false) }
                LaunchedEffect(champion?.id, game.entries.size) {
                    winnerReady = false
                    if (champion != null && !celebrationDismissed) {
                        delay(1800)
                        winnerReady = true
                    }
                }
                if (champion != null && winnerReady && !celebrationDismissed) {
                    WinnerDialog(
                        standings = boardOrder.take(3).map { it to (totals[it.id] ?: 0) },
                        confettiColors = game.players.map { Color(it.color) },
                        onKeepPlaying = { celebrationDismissed = true },
                        onNewGame = { viewModel.restartWithSameSetup() },
                    )
                }

                game.players.forEachIndexed { i, player ->
                    // While dragging, the whole wheel of dots turns together
                    // with the touch (a real rotary dial's disk), rather than
                    // just the active player's own trail moving in place.
                    // Idle dots hide for the spin and fade back in with the
                    // settle animation instead of popping.
                    val isActiveDot = activeId == null || activeId == player.id
                    val a = if (activeId != null) dotAngles[i] + accRadians else dotAngles[i]
                    SeatDot(
                        color = Color(player.color),
                        sizeDp = dp(dotD),
                        alpha = if (isActiveDot) 1f else settle,
                        offsetPx = IntOffset(
                            x = (cx + cos(a).toFloat() * ringR - dotD / 2f).toInt(),
                            y = (cy + sin(a).toFloat() * ringR - dotD / 2f).toInt(),
                        ),
                        onTap = {
                            if (game.hapticsEnabled) {
                                buzz(ctx, 30, game.hapticStrength)
                            }
                            viewModel.addScore(player.id, game.tapPoints)
                            showFlash(player.color, game.tapPoints, player.name)
                        },
                        animatePlacement = activeId == null,
                    )
                }

                // Render all labels (positions already clamped on-screen by
                // computeLabelPositions, or fitted to their zone in team
                // mode).
                // Tap-to-rotate yields while a swap gesture is in flight or
                // just ended (the release tap would otherwise also rotate).
                fun tapAllowed(): Boolean =
                    !swapEngaged && seatDrag.draggedId == null &&
                        System.nanoTime() - swapEndTime > 250_000_000L
                // Haptic + dial feedback. Reads current state at call time,
                // so detectors may hold this reference across swaps.
                fun feedback(millis: Long) {
                    if (latestGame.hapticsEnabled) {
                        buzz(ctx, millis, latestGame.hapticStrength)
                    }
                }
                if (!zoned || singleFull) {
                    game.players.forEachIndexed { i, player ->
                        val pos = labelPositions[i]!!
                        val isActive = activeId == player.id
                        val off = IntOffset(
                            x = (pos.x - memberBoxPx[i] / 2f).toInt(),
                            y = (pos.y - memberBoxPx[i] / 2f).toInt(),
                        )
                        key(player.id) {
                            SeatScore(
                                player = player,
                                // The edge score always shows the player's committed
                                // total; the live spin delta lives only in the center
                                // readout while swiping, then commits on release.
                                score = totals[player.id] ?: 0,
                                rank = playerRanks[i],
                                showName = game.showPlayerNames,
                                scoreSp = memberScoreSp(memberBoxPx[i]),
                                boxDp = dp(memberBoxPx[i]),
                                dimmed = activeId != null && !isActive,
                                offsetPx = off,
                                geo = boardGeoState,
                                drag = seatDrag,
                                isDragged = seatDrag.draggedId == player.id,
                                feedback = { millis ->
                                    if (latestGame.hapticsEnabled) {
                                        buzz(ctx, millis, latestGame.hapticStrength)
                                    }
                                },
                                onSwapStart = {
                                    swapEngaged = true
                                    activeId = null
                                    pending = 0
                                },
                                onSwapPair = { target ->
                                    viewModel.swapPlayers(player.id, target)
                                },
                                onSwapEnd = {
                                    // A drop in a foreign zone moves teams
                                    // (null = going solo); otherwise live
                                    // swaps already applied. Whichever
                                    // teardown runs first fully recovers,
                                    // the other is a no-op.
                                    val dropped = seatDrag.draggedId
                                    if (seatDrag.dropArmed && dropped != null) {
                                        viewModel.assignPlayer(dropped, seatDrag.dropTeamId)
                                    }
                                    seatDrag.release()
                                    swapEngaged = false
                                    swapEndTime = System.nanoTime()
                                },
                                glideBase = seatDrag.draggedId != player.id &&
                                    System.nanoTime() - swapEndTime > 350_000_000L,
                                onTap = {
                                    if (tapAllowed()) {
                                        if (game.hapticsEnabled) {
                                            buzz(ctx, 20, game.hapticStrength)
                                        }
                                        viewModel.rotatePlayer(player.id)
                                    }
                                },
                            )
                        }
                    }
                } else {
                    // Team zones, pass two: member labels in a fitted grid.
                    // (Tints and total chips rendered in the pass above the
                    // ring, so the dial always draws over them.)
                    zones.forEach { zone ->
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(zone.rect.l.toInt(), zone.rect.t.toInt()) }
                                .size(
                                    with(density) { (zone.rect.r - zone.rect.l).toDp() },
                                    with(density) { (zone.rect.b - zone.rect.t).toDp() },
                                )
                        ) {
                            zone.members.forEach { m ->
                                val player = game.players[m.playerIndex]
                                // Rows anchor at their computed left edge;
                                // squares stay centered on their slot.
                                val off = IntOffset(
                                    x = (m.local.x - m.rowW / 2f).toInt(),
                                    y = (m.local.y - m.boxPx / 2f).toInt(),
                                )
                                key(player.id) {
                                    SeatScore(
                                        player = player,
                                        score = totals[player.id] ?: 0,
                                        rank = playerRanks[m.playerIndex],
                                        showName = game.showPlayerNames,
                                        compact = zone.rowMode,
                                        rowWidthDp = dp(m.rowW),
                                        scoreSp = memberScoreSp(m.boxPx),
                                        boxDp = dp(m.boxPx),
                                        dimmed = activeId != null && activeId != player.id,
                                        offsetPx = off,
                                        geo = boardGeoState,
                                        drag = seatDrag,
                                        isDragged = seatDrag.draggedId == player.id,
                                        feedback = { millis ->
                                            if (latestGame.hapticsEnabled) {
                                                buzz(ctx, millis, latestGame.hapticStrength)
                                            }
                                        },
                                        onSwapStart = {
                                            swapEngaged = true
                                            activeId = null
                                            pending = 0
                                        },
                                        onSwapPair = { target ->
                                            viewModel.swapPlayers(player.id, target)
                                        },
                                        onSwapEnd = {
                                            // A drop in a foreign zone moves
                                            // teams (null = going solo);
                                            // otherwise live swaps already
                                            // applied. Whichever teardown runs
                                            // first fully recovers, the other
                                            // is a no-op.
                                            val dropped = seatDrag.draggedId
                                            if (seatDrag.dropArmed && dropped != null) {
                                                viewModel.assignPlayer(dropped, seatDrag.dropTeamId)
                                            }
                                            seatDrag.release()
                                            swapEngaged = false
                                            swapEndTime = System.nanoTime()
                                        },
                                        glideBase = seatDrag.draggedId != player.id &&
                                            System.nanoTime() - swapEndTime > 350_000_000L,
                                        onTap = {
                                            if (tapAllowed()) {
                                                if (game.hapticsEnabled) {
                                                    buzz(ctx, 20, game.hapticStrength)
                                                }
                                                viewModel.rotatePlayer(player.id)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // Non-interactable strip along the bottom: transparent, but eats
            // every touch so nothing below the layout — and no
            // system-gesture swipe passing through — can reach the dial, a
            // dot, or a label. Taps are swallowed by the no-op click target,
            // drags by the consuming drag detector (both sit above the board
            // surface, so nothing below ever sees the gesture).
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(deadZoneFrac)
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ -> change.consume() }
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
            )
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
    // The sweep-gradient shader is rebuilt only when the tip moves ~3°:
    // identical pixels, far fewer shader objects per drag.
    val tipDeg = arcStartDeg + arcSweepDeg
    val clockwise = arcSweepDeg >= 0f
    val tipQ = (tipDeg / 3f).roundToInt()
    val trailBrush = remember(activeColor, tipQ, clockwise, diameterPx) {
        Brush.sweepGradient(
            colors = trailFadeColors(activeColor ?: Color.Transparent, tipQ * 3f, clockwise = clockwise),
            center = Offset(diameterPx / 2f, diameterPx / 2f),
        )
    }
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
    val stops = 64
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

/**
 * Winner celebration for race-to-target games: confetti over the whole
 * screen, trophy + winner name, second and third below. Dependency-free
 * confetti — a single Canvas driven by one 0->1 animation.
 */
@Composable
private fun WinnerDialog(
    standings: List<Pair<Player, Int>>,
    confettiColors: List<Color>,
    onKeepPlaying: () -> Unit,
    onNewGame: () -> Unit,
) {
    val winner = standings.firstOrNull()
    Dialog(
        onDismissRequest = onKeepPlaying,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ConfettiOverlay(colors = confettiColors, modifier = Modifier.fillMaxSize())
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .fillMaxWidth(),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(24.dp),
                ) {
                    Icon(
                        Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(56.dp),
                    )
                    if (winner != null) {
                        Text(
                            text = winner.first.name,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(winner.first.color),
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = "wins!",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    standings.drop(1).forEachIndexed { i, (player, score) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "#${i + 2} ${player.name}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "$score",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(player.color),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        TextButton(
                            onClick = onKeepPlaying,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Keep playing")
                        }
                        Button(
                            onClick = onNewGame,
                            shape = MaterialTheme.shapes.extraLarge,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("New game")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfettiOverlay(colors: List<Color>, modifier: Modifier = Modifier) {
    data class Piece(
        val x0: Float,
        val delay: Float,
        val vx: Float,
        val vy: Float,
        val size: Float,
        val color: Color,
        val rot0: Float,
        val vr: Float,
        val sway: Float,
    )
    val pieces = remember(colors) {
        val rnd = Random(7)
        val palette = colors.ifEmpty { listOf(Color.White) }
        List(130) {
            Piece(
                x0 = rnd.nextFloat(),
                delay = rnd.nextFloat() * 0.35f,
                vx = (rnd.nextFloat() - 0.5f) * 0.12f,
                vy = 0.55f + rnd.nextFloat() * 0.6f,
                size = 8f + rnd.nextFloat() * 14f,
                color = palette[rnd.nextInt(palette.size)],
                rot0 = rnd.nextFloat() * 360f,
                vr = (rnd.nextFloat() - 0.5f) * 720f,
                sway = 0.02f + rnd.nextFloat() * 0.05f,
            )
        }
    }
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        animate(0f, 1f, animationSpec = tween(durationMillis = 2800)) { value, _ ->
            progress = value
        }
    }
    Canvas(modifier = modifier) {
        pieces.forEach { p ->
            val t = ((progress - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
            if (t <= 0f) return@forEach
            val x = (p.x0 + p.vx * t + sin(t * 9f + p.rot0) * p.sway) * size.width
            val y = -40f + (size.height + 80f) * p.vy * t
            val alpha = ((1f - t) / 0.3f).coerceIn(0f, 1f)
            rotate(p.rot0 + p.vr * t, pivot = Offset(x, y)) {
                drawRect(
                    color = p.color.copy(alpha = alpha),
                    topLeft = Offset(x, y),
                    size = Size(p.size, p.size * 0.6f),
                )
            }
        }
    }
}

/**
 * Zone background + total chip, rendered underneath the ring, dots and
 * labels. The tint is cut around the dial by painting the board
 * background back over it — a plain overpaint, so it renders identically
 * everywhere. Chips are display only and never consume touches.
 */
@Composable
private fun BoxScope.ZoneBackdrop(
    zone: Zone,
    ringR: Float,
    dotD: Float,
    cx: Float,
    cy: Float,
    density: Density,
    targeted: Boolean,
    total: Int,
    rank: Int,
) {
    val team = zone.team
    val d = LocalDensity.current
    if (team != null) {
        val tintColor = Color(team.color)
        val tintAlpha = if (targeted) 0.22f else 0.08f
        val boardBg = MaterialTheme.colorScheme.background
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRoundRect(
                color = tintColor.copy(alpha = tintAlpha),
                cornerRadius = CornerRadius(with(d) { 24.dp.toPx() }),
            )
                                    // Hole matches the row layout band: ring
                                    // outer edge plus a small margin.
                                    drawCircle(
                                        color = boardBg,
                                        radius = ringR + dotD * 0.5f + with(d) { 8.dp.toPx() },
                                        center = Offset(cx - zone.rect.l, cy - zone.rect.t),
                                    )
        }
        TeamTotalChip(
            team = team,
            total = total,
            rank = rank,
            modifier = Modifier
                .align(zone.corner)
                .padding(12.dp),
        )
    } else if (targeted) {
        // Solo zone lights up when a drop would free the carried player
        // from their team.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.35f)
                ),
        )
    }
}

/**
 * Podium rank per score: 1 + the number of strictly better scores, so ties
 * share a rank (same rule as the leaderboard). Empty until scores differ,
 * so a fresh board doesn't crown everyone "1st".
 */
private fun podiumRanks(scores: List<Int>, lowestWins: Boolean): List<Int> {
    if (scores.distinct().size < 2) return scores.map { 0 }
    return scores.map { sc -> scores.count { if (lowestWins) it < sc else it > sc } + 1 }
}

/** Gold/silver/bronze trophy (leaderboard colors); nothing outside the top three. */
@Composable
private fun RankBadge(rank: Int, modifier: Modifier = Modifier) {
    val medal = medalFor(rank) ?: return
    Icon(
        imageVector = Icons.Filled.EmojiEvents,
        contentDescription = when (rank) { 1 -> "1st place"; 2 -> "2nd place"; else -> "3rd place" },
        tint = medal,
        modifier = modifier.size(20.dp),
    )
}

/** Team name + collective total. Display only — never consumes touches. */@Composable
private fun TeamTotalChip(team: Team, total: Int, rank: Int, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = team.name,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "$total",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color(team.color),
        )
    }
    RankBadge(
        rank = rank,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 8.dp, bottom = 2.dp),
    )
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
    animatePlacement: Boolean = true,
) {
    // Glide between seats on swaps. Raw while the dial is turning, where
    // the target moves every frame and any lag would detach the dots.
    val glidingOffset by animateIntOffsetAsState(
        targetValue = offsetPx,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "dotSlide",
    )
    Box(
        modifier = Modifier
            .offset { if (animatePlacement) glidingOffset else offsetPx }
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
 *
 * Long-press picks the label up: it lifts, follows the finger, and live-
 * swaps with whatever seat it crosses (like roster reorder). Geometry
 * comes only through [geo], because this tracker outlives the swaps it
 * causes and captured arrays would go stale after the first one.
 */
@Composable
private fun SeatScore(
    player: Player,
    score: Int,
    rank: Int,
    showName: Boolean,
    scoreSp: Float,
    boxDp: Dp,
    dimmed: Boolean,
    offsetPx: IntOffset,
    onTap: () -> Unit,
    geo: State<BoardGeo>,
    drag: SeatDragState,
    isDragged: Boolean,
    feedback: (Long) -> Unit,
    onSwapStart: () -> Unit,
    onSwapPair: (String) -> Unit,
    onSwapEnd: () -> Unit,
    glideBase: Boolean = true,
    // Compact rows (packed zones): full-width strips with name + score
    // side by side instead of small squares. Still draggable; tap and
    // rotation are dropped (a wide strip can't turn inside its bounds).
    compact: Boolean = false,
    /** Row width in dp (compact rows); null keeps the square box. */
    rowWidthDp: Dp? = null,
) {
    val color = Color(player.color)
    // Count up/down to the new total instead of jumping to it. At rest the
    // animated value already equals the target, so idle frames are static.
    val shown by animateIntAsState(
        targetValue = score,
        animationSpec = tween(durationMillis = 1200),
        label = "scoreCount",
    )
    val text = "$shown"
    val digits = text.filter { it.isDigit() }.length
    val size = (scoreSp - (digits - 1).coerceAtLeast(0) * 2.5f).coerceAtLeast(14f)
    // Carry translation lives in the holder (restart-proof), not here:
    // finger minus grab point, both in board coords. The offset lambda
    // below restarts on change without recomposing, so following the
    // finger costs relayout, not redraw.
    val carry = if (drag.draggedId == player.id) drag.carry else Offset.Zero
    // Glide between seats on swaps. Suspended while carried and briefly
    // after the drop, so the visual never lags the finger or replays the
    // trip once released — the base tracks the layout exactly instead.
    // (The animation keeps running underneath and is settled by the time
    // gliding resumes, so the handoff is invisible.)
    val animatedBase by animateIntOffsetAsState(
        targetValue = offsetPx,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "seatSlide",
    )
    val glidingBase = if (glideBase) animatedBase else offsetPx
    // Rows span their computed width at the layout's left edge; squares
    // keep their box. The drag offset applies in both (carry tracks the
    // finger either way).
    val placeX = glidingBase.x
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (placeX + carry.x).roundToInt(),
                    (glidingBase.y + carry.y).toInt(),
                )
            }
            .then(
                if (compact) {
                    (if (rowWidthDp != null) Modifier.width(rowWidthDp) else Modifier.fillMaxWidth())
                        .height(boxDp)
                } else Modifier.size(boxDp)
            )
            .alpha(if (dimmed) 0.4f else 1f)
            .pointerInput(player.id) {
                // Deliberately stateless: all tracking lives in [drag], so
                // a detector restart mid-gesture (live swaps change keys
                // upstream) loses nothing — the next event continues clean.
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        val g = geo.value
                        val idx = g.players.indexOfFirst { it.id == player.id }
                        val c = g.centers.getOrNull(idx)
                        if (idx < 0 || c == null || !drag.claim(player.id, c)) {
                            return@detectDragGesturesAfterLongPress
                        }
                        onSwapStart()
                        feedback(30)
                    },
                    onDrag = { change, amount ->
                        if (drag.draggedId != player.id) return@detectDragGesturesAfterLongPress
                        change.consume()
                        drag.move(
                            Offset(amount.x, amount.y),
                            geo.value,
                            onSwap = { target -> onSwapPair(target) },
                            onTick = { feedback(15) },
                        )
                    },
                    onDragEnd = {
                        if (drag.draggedId != player.id) return@detectDragGesturesAfterLongPress
                        // Resolution (team join, release, timestamps) lives
                        // in onSwapEnd so drop state is read before clearing.
                        onSwapEnd()
                    },
                    onDragCancel = {
                        if (drag.draggedId != player.id) return@detectDragGesturesAfterLongPress
                        onSwapEnd()
                    },
                )
            }
            .rotate(if (compact) 0f else RotationUtils.degrees(player.rotation))
            .border(
                width = 2.dp,
                color = if (isDragged) color else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
            )
            .graphicsLayer {
                // Both branches assigned: graphicsLayer properties persist
                // across invocations, so the lifted look would otherwise
                // stick after the drop.
                if (isDragged) {
                    scaleX = 1.06f
                    scaleY = 1.06f
                    shadowElevation = 24f
                } else {
                    scaleX = 1f
                    scaleY = 1f
                    shadowElevation = 0f
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                // Compact rows don't rotate (a full-width strip can't turn
                // inside its bounds); taps there do nothing, drags work.
                onClick = { if (!compact) onTap() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Compact rows read name + score side by side at full width;
        // roomy labels keep the stacked square.
        val nameSize = (if (compact) size * 0.62f else size * 0.36f).coerceAtLeast(12f)
        if (compact) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            ) {
                if (showName) {
                    Text(
                        text = player.name,
                        fontSize = nameSize.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = color,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    text = text,
                    fontSize = size.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    maxLines = 1,
                    textAlign = TextAlign.End,
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (showName) {
                Text(
                    text = player.name,
                    // Name-to-score ratio mirrors the reference layout;
                    // always one weight below the Bold score itself.
                    fontSize = (size * 0.36f).coerceAtLeast(11f).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
            // Trophy hangs off the digits' bottom-right corner, not the
            // label box's.
            Box {
                Text(
                    text = text,
                    fontSize = size.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
                RankBadge(
                    rank,
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 14.dp, y = (-2).dp),
                )
            }
        }
        }
    }
}
