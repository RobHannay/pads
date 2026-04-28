package com.worshippads

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worshippads.audio.EqResponse
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val MIN_FREQ = 20f
private const val MAX_FREQ = 20_000f
private const val MIN_DB = -6f
private const val MAX_DB = 6f
private const val CURVE_SAMPLES = 200
private const val HIT_RADIUS_DP = 36f

private enum class Band { LOW_CUT, BASS, PRESENCE, TREBLE }

private fun freqToXFrac(freqHz: Float): Float {
    val safe = freqHz.coerceIn(MIN_FREQ, MAX_FREQ)
    return (log10(safe / MIN_FREQ) / log10(MAX_FREQ / MIN_FREQ)).toFloat()
}

private fun xFracToFreq(xFrac: Float): Float {
    val f = xFrac.coerceIn(0f, 1f)
    return MIN_FREQ * 10.0.pow((f * log10(MAX_FREQ / MIN_FREQ)).toDouble()).toFloat()
}

private fun dbToYFrac(db: Float): Float = ((MAX_DB - db) / (MAX_DB - MIN_DB)).coerceIn(0f, 1f)

private fun yFracToDb(yFrac: Float): Float =
    MAX_DB - yFrac.coerceIn(0f, 1f) * (MAX_DB - MIN_DB)

/**
 * Parametric-EQ style visualiser with four draggable handles (one per band).
 *
 * Bass / Presence / Treble are constrained to vertical drag (dB). Low cut is
 * constrained to horizontal drag (cutoff frequency) and snaps to Off below 40 Hz.
 * The response curve is the actual magnitude of the biquad chain — see
 * [EqResponse] — not a spline approximation.
 */
@Composable
fun EqGraphView(
    bassDb: Float,
    presenceDb: Float,
    trebleDb: Float,
    lowCutHz: Int,
    onBassChange: (Float) -> Unit,
    onPresenceChange: (Float) -> Unit,
    onTrebleChange: (Float) -> Unit,
    onLowCutChange: (Int) -> Unit,
    axisColor: Color,
    curveColor: Color,
    handleColor: Color,
    fillColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    var active by remember { mutableStateOf<Band?>(null) }
    val textMeasurer = rememberTextMeasurer()

    // Capture latest state + callbacks without re-keying pointerInput
    // (which would cancel and restart the gesture coroutine on every drag tick).
    val bassState by rememberUpdatedState(bassDb)
    val presenceState by rememberUpdatedState(presenceDb)
    val trebleState by rememberUpdatedState(trebleDb)
    val lowCutState by rememberUpdatedState(lowCutHz)
    val onBass by rememberUpdatedState(onBassChange)
    val onPresence by rememberUpdatedState(onPresenceChange)
    val onTreble by rememberUpdatedState(onTrebleChange)
    val onLowCut by rememberUpdatedState(onLowCutChange)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .pointerInput(Unit) {
                val hitPx = HIT_RADIUS_DP.dp.toPx()
                awaitEachGesture {
                    val down = awaitPointerEvent().changes.first()
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    val hit = hitTestBand(
                        down.position,
                        Size(w, h),
                        bassState, presenceState, trebleState, lowCutState,
                        hitPx,
                    ) ?: return@awaitEachGesture
                    active = hit
                    down.consume()
                    // Don't snap on touch down — only on subsequent movement.
                    do {
                        val ev = awaitPointerEvent()
                        val change = ev.changes.first()
                        if (!change.pressed) {
                            active = null
                            change.consume()
                            return@awaitEachGesture
                        }
                        val p = change.position
                        // Round drag to nearest 0.1 dB — keeps the display
                        // honest without showing fractional values like +2.347.
                        fun snapDb(raw: Float) =
                            ((raw * 10f).roundToInt() / 10f).coerceIn(MIN_DB, MAX_DB)
                        when (hit) {
                            Band.BASS -> onBass(snapDb(yFracToDb(p.y / h)))
                            Band.PRESENCE -> onPresence(snapDb(yFracToDb(p.y / h)))
                            Band.TREBLE -> onTreble(snapDb(yFracToDb(p.y / h)))
                            Band.LOW_CUT -> {
                                val freq = xFracToFreq(p.x / w).roundToInt()
                                onLowCut(freq.coerceIn(20, 200))
                            }
                        }
                        change.consume()
                    } while (true)
                }
            }
    ) {
        drawGrid(axisColor, textColor, textMeasurer)
        drawIndividualBandCurves(bassDb, presenceDb, trebleDb, lowCutHz, curveColor)
        drawCurve(bassDb, presenceDb, trebleDb, lowCutHz, curveColor, fillColor)
        drawHandles(bassDb, presenceDb, trebleDb, lowCutHz, active, handleColor, textColor, textMeasurer)
    }
}

/**
 * Faint per-band response curves drawn underneath the cumulative curve.
 * Each dot sits exactly on its own band's curve, even when a strong neighbour
 * is pulling the cumulative response away from the band's centre frequency.
 */
private fun DrawScope.drawIndividualBandCurves(
    bassDb: Float,
    presenceDb: Float,
    trebleDb: Float,
    lowCutHz: Int,
    color: Color,
) {
    val faint = color.copy(alpha = 0.28f)
    if (bassDb != 0f) drawSingleBandPath(bassDb, 0f, 0f, 0, faint)
    if (presenceDb != 0f) drawSingleBandPath(0f, presenceDb, 0f, 0, faint)
    if (trebleDb != 0f) drawSingleBandPath(0f, 0f, trebleDb, 0, faint)
    if (lowCutHz > 0) drawSingleBandPath(0f, 0f, 0f, lowCutHz, faint)
}

private fun DrawScope.drawSingleBandPath(
    bassDb: Float,
    presenceDb: Float,
    trebleDb: Float,
    lowCutHz: Int,
    color: Color,
) {
    val w = size.width
    val h = size.height
    val path = Path()
    for (i in 0..CURVE_SAMPLES) {
        val xFrac = i.toFloat() / CURVE_SAMPLES
        val freq = xFracToFreq(xFrac)
        val db = EqResponse.responseDb(bassDb, presenceDb, trebleDb, lowCutHz, freq)
        val y = dbToYFrac(db.coerceIn(MIN_DB, MAX_DB)) * h
        val x = xFrac * w
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, color = color, style = Stroke(width = 1.4f.dp.toPx()))
}

private fun DrawScope.drawGrid(
    axisColor: Color,
    textColor: Color,
    textMeasurer: TextMeasurer,
) {
    val w = size.width
    val h = size.height

    // Horizontal lines at +3, 0, -3 dB
    for (db in listOf(3f, 0f, -3f)) {
        val y = dbToYFrac(db) * h
        drawLine(
            color = axisColor.copy(alpha = if (db == 0f) 0.35f else 0.18f),
            start = Offset(0f, y),
            end = Offset(w, y),
            strokeWidth = if (db == 0f) 1f else 0.6f.dp.toPx(),
        )
    }

    // Vertical lines at 100, 1k, 10k Hz with labels
    val labelStyle = TextStyle(color = textColor.copy(alpha = 0.4f), fontSize = 9.sp)
    for ((freq, label) in listOf(100f to "100", 1000f to "1k", 10000f to "10k")) {
        val x = freqToXFrac(freq) * w
        drawLine(
            color = axisColor.copy(alpha = 0.15f),
            start = Offset(x, 0f),
            end = Offset(x, h),
            strokeWidth = 0.6f.dp.toPx(),
        )
        val measured = textMeasurer.measure(label, labelStyle)
        drawText(
            textMeasurer = textMeasurer,
            text = label,
            topLeft = Offset(x + 3.dp.toPx(), h - measured.size.height - 2.dp.toPx()),
            style = labelStyle,
        )
    }

    // +3 / −3 labels on the left edge
    for ((db, label) in listOf(3f to "+3", -3f to "−3")) {
        val y = dbToYFrac(db) * h
        drawText(
            textMeasurer = textMeasurer,
            text = label,
            topLeft = Offset(4.dp.toPx(), y - 6.sp.toPx()),
            style = labelStyle,
        )
    }
}

private fun DrawScope.drawCurve(
    bassDb: Float,
    presenceDb: Float,
    trebleDb: Float,
    lowCutHz: Int,
    curveColor: Color,
    fillColor: Color,
) {
    val w = size.width
    val h = size.height
    val path = Path()
    val fill = Path()
    val yCenter = dbToYFrac(0f) * h

    for (i in 0..CURVE_SAMPLES) {
        val xFrac = i.toFloat() / CURVE_SAMPLES
        val freq = xFracToFreq(xFrac)
        val db = EqResponse.responseDb(bassDb, presenceDb, trebleDb, lowCutHz, freq)
        val y = dbToYFrac(db.coerceIn(MIN_DB, MAX_DB)) * h
        val x = xFrac * w
        if (i == 0) {
            path.moveTo(x, y)
            fill.moveTo(x, yCenter)
            fill.lineTo(x, y)
        } else {
            path.lineTo(x, y)
            fill.lineTo(x, y)
        }
    }
    fill.lineTo(w, yCenter)
    fill.close()

    drawPath(fill, brush = Brush.verticalGradient(
        colors = listOf(fillColor, fillColor.copy(alpha = 0f)),
        startY = 0f,
        endY = h,
    ))
    drawPath(path, color = curveColor, style = Stroke(width = 2.5f.dp.toPx()))
}

private fun DrawScope.drawHandles(
    bassDb: Float,
    presenceDb: Float,
    trebleDb: Float,
    lowCutHz: Int,
    active: Band?,
    handleColor: Color,
    textColor: Color,
    textMeasurer: TextMeasurer,
) {
    val w = size.width
    val h = size.height

    drawHandle(
        center = bandCenter(Band.BASS, Size(w, h), bassDb, presenceDb, trebleDb, lowCutHz),
        isActive = active == Band.BASS,
        color = handleColor,
    )
    drawHandle(
        center = bandCenter(Band.PRESENCE, Size(w, h), bassDb, presenceDb, trebleDb, lowCutHz),
        isActive = active == Band.PRESENCE,
        color = handleColor,
    )
    drawHandle(
        center = bandCenter(Band.TREBLE, Size(w, h), bassDb, presenceDb, trebleDb, lowCutHz),
        isActive = active == Band.TREBLE,
        color = handleColor,
    )
    drawHandle(
        center = bandCenter(Band.LOW_CUT, Size(w, h), bassDb, presenceDb, trebleDb, lowCutHz),
        isActive = active == Band.LOW_CUT,
        color = handleColor,
    )

    // Small per-handle labels ("Bass", etc) drawn just above each handle.
    val labelStyle = TextStyle(color = textColor.copy(alpha = 0.75f), fontSize = 10.sp)
    val bands = listOf(Band.LOW_CUT, Band.BASS, Band.PRESENCE, Band.TREBLE)
    for (band in bands) {
        val center = bandCenter(band, Size(w, h), bassDb, presenceDb, trebleDb, lowCutHz)
        val text = when (band) {
            Band.LOW_CUT -> "HPF"
            Band.BASS -> "Bass"
            Band.PRESENCE -> "Pres"
            Band.TREBLE -> "Treb"
        }
        val measured = textMeasurer.measure(text, labelStyle)
        val tx = (center.x - measured.size.width / 2f).coerceIn(2.dp.toPx(), w - measured.size.width - 2.dp.toPx())
        val ty = (center.y - 20.dp.toPx()).coerceAtLeast(2.dp.toPx())
        drawText(
            textMeasurer = textMeasurer,
            text = text,
            topLeft = Offset(tx, ty),
            style = labelStyle,
        )
    }
}

private fun DrawScope.drawHandle(center: Offset, isActive: Boolean, color: Color) {
    val radius = (if (isActive) 11f else 8f).dp.toPx()
    drawCircle(
        color = color.copy(alpha = 0.25f),
        radius = radius + 4.dp.toPx(),
        center = center,
    )
    drawCircle(color = color, radius = radius, center = center)
    drawCircle(color = Color.White, radius = radius - 3.dp.toPx(), center = center)
}

private const val FREQ_BASS = 150f
private const val FREQ_PRESENCE = 2500f
private const val FREQ_TREBLE = 6000f

/**
 * Each handle reflects its own band's setting, not the running total of
 * all four bands at that frequency. Dragging one band therefore never
 * visibly moves a neighbour's dot. The curve drawn through the chart is
 * the actual filter sum, so the dots may sit slightly above or below the
 * curve where bands overlap — that's expected.
 *
 * HPF is the exception: it has no dB setting, so its dot rides the curve
 * vertically (and its X position represents the cutoff frequency, the
 * thing that actually drives it).
 */
private fun bandFrac(
    band: Band,
    bassDb: Float,
    presenceDb: Float,
    trebleDb: Float,
    lowCutHz: Int,
): Offset = when (band) {
    Band.BASS -> Offset(freqToXFrac(FREQ_BASS), dbToYFrac(bassDb))
    Band.PRESENCE -> Offset(freqToXFrac(FREQ_PRESENCE), dbToYFrac(presenceDb))
    Band.TREBLE -> Offset(freqToXFrac(FREQ_TREBLE), dbToYFrac(trebleDb))
    // HPF dot sits at (cutoff, 0 dB) — the "corner" where the cut starts.
    // The cut itself is shown by the faint individual band curve sloping
    // below.
    Band.LOW_CUT -> Offset(freqToXFrac(lowCutHz.toFloat()), dbToYFrac(0f))
}

private fun bandCenter(
    band: Band,
    size: Size,
    bassDb: Float,
    presenceDb: Float,
    trebleDb: Float,
    lowCutHz: Int,
): Offset {
    val frac = bandFrac(band, bassDb, presenceDb, trebleDb, lowCutHz)
    return Offset(frac.x * size.width, frac.y * size.height)
}

private fun hitTestBand(
    pos: Offset,
    size: Size,
    bassDb: Float,
    presenceDb: Float,
    trebleDb: Float,
    lowCutHz: Int,
    hitRadiusPx: Float,
): Band? {
    var best: Band? = null
    var bestDist = hitRadiusPx
    for (band in Band.entries) {
        val c = bandCenter(band, size, bassDb, presenceDb, trebleDb, lowCutHz)
        val d = sqrt((pos.x - c.x).pow(2) + (pos.y - c.y).pow(2))
        if (d < bestDist) {
            bestDist = d
            best = band
        }
    }
    return best
}

