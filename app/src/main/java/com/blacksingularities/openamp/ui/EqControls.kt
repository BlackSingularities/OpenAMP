package com.blacksingularities.openamp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.openamp.i18n.tr
import com.blacksingularities.openamp.playback.Dsp
import com.blacksingularities.openamp.playback.DspMeter
import com.blacksingularities.openamp.playback.DspState
import com.blacksingularities.openamp.playback.EqBand
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 * Kontrolki ekranu korektora w płaskim języku aplikacji (Flat): cienkie ścieżki w kolorze Hair,
 * wypełnienie akcentem od wartości neutralnej, białe (Fg) uchwyty, bez gradientów i obwódek.
 * Każda kontrolka zgłasza początek gestu ([onStart]) – tam korektor zapisuje punkt cofania.
 */

private val Danger = Color(0xFFFF6B6B)

fun freqLabel(hz: Float): String = if (hz >= 1000) {
    val k = hz / 1000f
    if (k >= 10 || k == k.roundToInt().toFloat()) "${k.roundToInt()}k" else String.format(Locale.ROOT, "%.1fk", k)
} else "${hz.roundToInt()}"

/** Częstotliwość z jednostką: „125 Hz”, „1.25 kHz”. */
fun freqText(hz: Float): String = when {
    hz >= 10000 -> String.format(Locale.ROOT, "%.1f kHz", hz / 1000f)
    hz >= 1000 -> String.format(Locale.ROOT, "%.2f kHz", hz / 1000f)
    else -> "${hz.roundToInt()} Hz"
}

fun dbText(v: Float) = String.format(Locale.ROOT, "%+.1f", v).replace("-", "−")

/** Fader pasma: wartość u góry, pionowa ścieżka z wypełnieniem od zera, podpis u dołu. */
@Composable
fun EqFader(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    onSelect: () -> Unit = {},
    onStart: () -> Unit = {},
    onChange: (Float) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val fg = Flat.Fg
    val hair = Flat.Hair
    val change by rememberUpdatedState(onChange)
    val start by rememberUpdatedState(onStart)
    val select by rememberUpdatedState(onSelect)
    val span = range.endInclusive - range.start
    val haptic = LocalHapticFeedback.current
    Column(modifier.alpha(if (enabled) 1f else 0.4f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            (if (abs(value) < 0.05f) "0" else dbText(value).removeSuffix(".0")), fontFamily = MonoNumbers, fontSize = 10.sp, maxLines = 1,
            color = when { selected -> accent; abs(value) > 0.01f -> fg; else -> Flat.Faded },
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
        Canvas(
            Modifier.fillMaxWidth().height(168.dp)
                .semantics {
                    contentDescription = label
                    stateDescription = "${dbText(value)} dB"
                    progressBarRangeInfo = ProgressBarRangeInfo(value, range)
                    if (enabled) setProgress { v -> change(v.coerceIn(range.start, range.endInclusive)); true }
                }
                // Tylko pionowe przeciąganie – przy wielu pasmach poziomy gest przewija rząd faderów.
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    val pad = 10.dp.toPx()
                    fun v(y: Float) = (range.endInclusive - (y - pad) / (size.height - 2 * pad) * span).coerceIn(range.start, range.endInclusive)
                    detectVerticalDragGestures(
                        onDragStart = { o -> select(); start(); change(v(o.y)) },
                    ) { ch, _ -> ch.consume(); change(v(ch.position.y)) }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onTap = { select() },
                        onDoubleTap = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); start(); change(0f.coerceIn(range.start, range.endInclusive)) },
                    )
                },
        ) {
            val pad = 10.dp.toPx()
            val h = size.height - 2 * pad
            val cx = size.width / 2
            fun y(v: Float) = pad + (range.endInclusive - v) / span * h
            val tw = 4.dp.toPx()
            drawRoundRect(hair, Offset(cx - tw / 2, pad), Size(tw, h), CornerRadius(tw))
            // Podziałka co 5 dB, mocniej przy zerze.
            var t = range.start
            while (t <= range.endInclusive + 0.01f) {
                val zero = abs(t) < 0.01f
                val len = if (zero) 9.dp.toPx() else 4.dp.toPx()
                drawLine(if (zero) fg.copy(alpha = 0.35f) else hair, Offset(cx - tw - len, y(t)), Offset(cx - tw, y(t)), 1.dp.toPx())
                drawLine(if (zero) fg.copy(alpha = 0.35f) else hair, Offset(cx + tw, y(t)), Offset(cx + tw + len, y(t)), 1.dp.toPx())
                t += 5f
            }
            val y0 = y(0f.coerceIn(range.start, range.endInclusive)); val y1 = y(value)
            drawRoundRect(accent, Offset(cx - tw / 2, minOf(y0, y1)), Size(tw, abs(y1 - y0)), CornerRadius(tw))
            val thumbW = 22.dp.toPx(); val thumbH = 9.dp.toPx()
            drawRoundRect(if (selected) accent else fg, Offset(cx - thumbW / 2, y1 - thumbH / 2), Size(thumbW, thumbH), CornerRadius(thumbH / 2))
        }
        Text(
            label, fontSize = 10.sp, maxLines = 1, fontWeight = FontWeight.SemiBold,
            color = if (selected) accent else Flat.Dim,
        )
    }
}

/**
 * Poziomy suwak z podpisem i wartością: wypełnienie od [neutral], dotknięcie przeskakuje,
 * podwójne dotknięcie przywraca [default].
 */
@Composable
fun ValueSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    modifier: Modifier = Modifier,
    neutral: Float = range.start,
    default: Float = neutral,
    enabled: Boolean = true,
    step: Float = 0f,
    subtitle: String? = null,
    onStart: () -> Unit = {},
    onChange: (Float) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val fg = Flat.Fg
    val hair = Flat.Hair
    val change by rememberUpdatedState(onChange)
    val start by rememberUpdatedState(onStart)
    val span = range.endInclusive - range.start
    var dragging by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    fun snap(v: Float) = (if (step > 0f) (Math.round((v - range.start) / step) * step + range.start) else v).coerceIn(range.start, range.endInclusive)
    Column(modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f).padding(horizontal = 12.dp, vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 13.sp, color = Flat.Fg, maxLines = 1)
                if (subtitle != null) Text(subtitle, fontSize = 10.sp, lineHeight = 12.sp, color = Compact.TextDim)
            }
            Text(
                format(value), fontSize = 12.sp, fontFamily = MonoNumbers, fontWeight = FontWeight.Bold,
                color = if (abs(value - neutral) > span * 0.001f) accent else Flat.Faded,
            )
        }
        Canvas(
            Modifier.fillMaxWidth().height(30.dp)
                .semantics {
                    contentDescription = label
                    stateDescription = format(value)
                    progressBarRangeInfo = ProgressBarRangeInfo(value, range)
                    if (enabled) setProgress { v -> change(snap(v)); true }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    val pad = 9.dp.toPx()
                    fun v(x: Float) = snap(range.start + ((x - pad) / (size.width - 2 * pad)).coerceIn(0f, 1f) * span)
                    detectHorizontalDragGestures(
                        onDragStart = { o -> dragging = true; start(); change(v(o.x)) },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    ) { ch, _ -> ch.consume(); change(v(ch.position.x)) }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    val pad = 9.dp.toPx()
                    detectTapGestures(
                        onTap = { o -> start(); change(snap(range.start + ((o.x - pad) / (size.width - 2 * pad)).coerceIn(0f, 1f) * span)) },
                        onDoubleTap = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); start(); change(default) },
                    )
                },
        ) {
            val pad = 9.dp.toPx()
            val w = size.width - 2 * pad
            val cy = size.height / 2
            fun x(v: Float) = pad + (v - range.start) / span * w
            val th = 4.dp.toPx()
            drawRoundRect(hair, Offset(pad, cy - th / 2), Size(w, th), CornerRadius(th))
            if (neutral > range.start && neutral < range.endInclusive) {
                drawLine(fg.copy(alpha = 0.35f), Offset(x(neutral), cy - 6.dp.toPx()), Offset(x(neutral), cy + 6.dp.toPx()), 1.dp.toPx())
            }
            val a = x(neutral); val b = x(value)
            drawRoundRect(accent, Offset(minOf(a, b), cy - th / 2), Size(abs(b - a), th), CornerRadius(th))
            drawCircle(fg, if (dragging) 9.dp.toPx() else 7.dp.toPx(), Offset(b, cy))
        }
    }
}

/** Powtarzanie akcji przy przytrzymaniu (po 0,38 s, co 55 ms) – dla przycisków −/+. */
private fun Modifier.repeatOnHold(enabled: Boolean, onStart: () -> Unit, action: () -> Unit): Modifier = composed {
    val act by rememberUpdatedState(action)
    val st by rememberUpdatedState(onStart)
    val haptic = LocalHapticFeedback.current
    pointerInput(enabled) {
        if (!enabled) return@pointerInput
        coroutineScope {
            awaitEachGesture {
                awaitFirstDown()
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                st(); act()
                val job = launch {
                    delay(380)
                    while (isActive) { act(); delay(55) }
                }
                waitForUpOrCancellation()
                job.cancel()
            }
        }
    }
}

/** Wartość z przyciskami −/+ (przytrzymanie powtarza). */
@Composable
fun Stepper(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onStart: () -> Unit = {},
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Column(modifier.alpha(if (enabled) 1f else 0.35f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = Compact.TextDim, maxLines = 1)
        Row(Modifier.padding(top = 4.dp).clip(Shapes.Pill).background(Flat.Faint), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(CircleShape).repeatOnHold(enabled, onStart, onMinus), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Remove, tr("Mniej"), tint = Flat.Fg, modifier = Modifier.size(16.dp))
            }
            Text(
                value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Flat.Fg, letterSpacing = (-0.2).sp,
                textAlign = TextAlign.Center, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f),
            )
            Box(Modifier.size(30.dp).clip(CircleShape).repeatOnHold(enabled, onStart, onPlus), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Add, tr("Więcej"), tint = Flat.Fg, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** Miniatura krzywej presetu (pasma w skali log 20 Hz – 20 kHz, ±12 dB). */
@Composable
fun CurveThumb(bands: List<EqBand>, color: Color, modifier: Modifier = Modifier) {
    val hair = color.copy(alpha = 0.18f)
    val points = remember(bands) { FloatArray(48) { i -> Dsp.bandsDb(bands, Dsp.F_MIN * (Dsp.F_MAX / Dsp.F_MIN).pow(i / 47f)) } }
    Canvas(modifier) {
        val mid = size.height / 2
        drawLine(hair, Offset(0f, mid), Offset(size.width, mid), 1.dp.toPx())
        val p = Path()
        points.forEachIndexed { i, d ->
            val x = size.width * i / (points.size - 1f)
            val y = mid - (d / 12f).coerceIn(-1f, 1f) * (mid - 2.dp.toPx())
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        drawPath(p, color, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Odświeżanie co klatkę dla mierników: zwraca licznik, który wymusza przerysowanie. */
@Composable
fun rememberFrameTick(active: Boolean = true): Int {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        while (true) { withFrameNanos { }; tick++ }
    }
    return tick
}

/** Odczyt procesora: szczyty L/P opadają ~20 dB/s, ściszenie dynamiki wraca ~10 dB/s. */
@Composable
fun rememberMeter(playing: Boolean): FloatArray {
    // [0] lewy dBFS, [1] prawy dBFS, [2] ściszenie dB; zmiana [3] wymusza przerysowanie.
    val v = remember { floatArrayOf(-60f, -60f, 0f, 0f) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(playing) {
        var last = 0L
        while (true) {
            val now = withFrameNanos { it }
            val dt = if (last == 0L) 0f else (now - last) / 1e9f
            last = now
            val live = playing && DspMeter.live()
            fun dbOf(x: Float) = if (live && x > 1e-5f) 20 * log10(x) else -60f
            v[0] = max(dbOf(DspMeter.peakL), v[0] - 20f * dt)
            v[1] = max(dbOf(DspMeter.peakR), v[1] - 20f * dt)
            v[2] = if (live) minOf(DspMeter.reductionDb, v[2] + 10f * dt).coerceAtMost(0f) else 0f
            tick++
        }
    }
    v[3] = tick.toFloat()
    return v
}

/** Pasek poziomu −60…0 dBFS: akcent, od −3 dB kolor ostrzegawczy; znaczniki co 12 dB. */
@Composable
fun LevelMeter(label: String, dbfs: Float) {
    val accent = MaterialTheme.colorScheme.primary
    val hair = Flat.Hair
    val bg = Flat.Bg
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Flat.Dim, modifier = Modifier.width(16.dp))
        Canvas(Modifier.weight(1f).height(8.dp)) {
            fun x(d: Float) = ((d + 60f) / 60f).coerceIn(0f, 1f) * size.width
            val r = CornerRadius(size.height / 2)
            drawRoundRect(hair, size = size, cornerRadius = r)
            val end = x(dbfs)
            clipRect(right = end) {
                drawRoundRect(accent, size = size, cornerRadius = r)
                clipRect(left = x(-3f)) { drawRoundRect(Danger, size = size, cornerRadius = r) }
            }
            for (d in listOf(-48f, -36f, -24f, -12f)) drawLine(bg.copy(alpha = 0.6f), Offset(x(d), 0f), Offset(x(d), size.height), 1.dp.toPx())
        }
        Text(
            if (dbfs <= -59.9f) "−∞" else String.format(Locale.ROOT, "%.1f", dbfs).replace("-", "−"),
            fontSize = 11.sp, fontFamily = MonoNumbers, color = Flat.Fg, textAlign = TextAlign.End, modifier = Modifier.width(48.dp),
        )
    }
}

/** Pasek ściszenia (0…−12 dB od prawej), jak wskaźnik redukcji w kompresorach. */
@Composable
fun ReductionBar(db: Float) {
    val accent = MaterialTheme.colorScheme.primary
    val hair = Flat.Hair
    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(tr("Ściszenie"), fontSize = 11.sp, color = Flat.Dim, modifier = Modifier.width(72.dp))
        Canvas(Modifier.weight(1f).height(6.dp)) {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(hair, size = size, cornerRadius = r)
            val f = (-db / 12f).coerceIn(0f, 1f)
            drawRoundRect(accent, Offset(size.width * (1 - f), 0f), Size(size.width * f, size.height), r)
        }
        Text(
            String.format(Locale.ROOT, "%.1f dB", db).replace("-", "−"), fontSize = 11.sp, fontFamily = MonoNumbers,
            color = Flat.Fg, textAlign = TextAlign.End, modifier = Modifier.width(64.dp),
        )
    }
}

/** Stan silnika: kropka (akcent – przetwarza dźwięk) i format toru. */
@Composable
fun EngineStatus(playing: Boolean, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    // Odczyt ze statycznych pól procesora – kilka razy na sekundę wystarczy.
    var now by remember { mutableStateOf(System.nanoTime()) }
    LaunchedEffect(Unit) { while (true) { delay(400); now = System.nanoTime() } }
    val live = DspMeter.live(now)
    val rate = DspMeter.sampleRate
    val text = when {
        live && rate > 0 -> buildString {
            append(String.format(Locale.ROOT, "%.1f kHz", rate / 1000f)).append(" · ").append(DspMeter.inputFormat)
            val n = DspMeter.filters
            append(" · ").append(if (n == 0) tr("bez filtrów") else com.blacksingularities.openamp.i18n.plural(n, "{0} filtr", "{0} filtry", "{0} filtrów"))
        }
        playing -> tr("Czekam na dźwięk z odtwarzacza…")
        else -> tr("Silnik gotowy – zacznij odtwarzanie")
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(if (live) accent else Flat.Faded))
        Text(text, fontSize = 10.sp, color = if (live) Flat.Dim else Flat.Faded, maxLines = 1, modifier = Modifier.padding(start = 6.dp))
    }
}

// ---------- Wykres charakterystyki ----------

private const val CURVE_N = 220

/**
 * Wykres charakterystyki: oś częstotliwości logarytmiczna (20 Hz – 20 kHz), siatka dB, widmo na
 * żywo pod spodem, kształt krzywej (pasma + półki), krzywa zaznaczonego pasma i uchwyty pasm.
 * Przerywana linia – całkowite wzmocnienie toru (przedwzmacniacz i automatyczny zapas).
 *
 * Przeciąganie uchwytu zmienia częstotliwość i wzmocnienie (przyrostowo – punkt nie skacze pod
 * palec), dotknięcie wybiera najbliższe pasmo, podwójne dotknięcie zeruje je.
 */
@Composable
fun EqGraph(
    s: DspState,
    live: LiveSpectrum,
    selected: Int,
    range: Float,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val fg = Flat.Fg
    val bg = Flat.Bg
    val cur by rememberUpdatedState(s)
    val rng by rememberUpdatedState(range)
    val select by rememberUpdatedState(onSelect)
    val sel by rememberUpdatedState(selected)
    val haptic = LocalHapticFeedback.current
    val measurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = Flat.Faded, fontSize = 9.sp)
    val tipStyle = MaterialTheme.typography.labelSmall.copy(color = bg, fontWeight = FontWeight.Bold, fontSize = 10.sp)
    val curve = remember(s) { FloatArray(CURVE_N) { Dsp.eqDb(s, Dsp.F_MIN * (Dsp.F_MAX / Dsp.F_MIN).pow(it / (CURVE_N - 1f))) } }
    val outGain = remember(s) { Dsp.outputGainDb(s) }
    val bandCurve = remember(s.bands, selected) {
        s.bands.getOrNull(selected)?.let { b -> FloatArray(CURVE_N) { Dsp.bandResponseDb(b, Dsp.F_MIN * (Dsp.F_MAX / Dsp.F_MIN).pow(it / (CURVE_N - 1f))) } }
    }

    Canvas(
        modifier
            .pointerInput(Unit) {
                val inset = 14.dp.toPx(); val padY = 14.dp.toPx()
                fun xOf(f: Float) = inset + ln(f / Dsp.F_MIN) / ln(Dsp.F_MAX / Dsp.F_MIN) * (size.width - 2 * inset)
                fun fOf(x: Float) = Dsp.F_MIN * (Dsp.F_MAX / Dsp.F_MIN).pow(((x - inset) / (size.width - 2 * inset)).coerceIn(0f, 1f))
                fun yOf(d: Float) = padY + (rng - d) / (2 * rng) * (size.height - 2 * padY)
                fun dOf(y: Float) = rng - (y - padY) / (size.height - 2 * padY) * 2 * rng
                var idx = -1
                var px = 0f; var py = 0f
                detectDragGestures(
                    onDragStart = { o ->
                        val bands = cur.bands
                        // Zaznaczone pasmo ma pierwszeństwo, jeśli palec jest blisko; inaczej najbliższy uchwyt.
                        val near = bands.indices.minByOrNull { hypot(xOf(bands[it].freq) - o.x, yOf(bands[it].gain) - o.y) } ?: -1
                        val s0 = sel
                        idx = if (s0 in bands.indices && hypot(xOf(bands[s0].freq) - o.x, yOf(bands[s0].gain) - o.y) < 40.dp.toPx()) s0 else near
                        if (idx >= 0 && cur.enabled) {
                            select(idx); Dsp.checkpoint()
                            px = xOf(bands[idx].freq); py = yOf(bands[idx].gain)
                        } else idx = -1
                    },
                    onDragEnd = { idx = -1 },
                    onDragCancel = { idx = -1 },
                ) { ch, d ->
                    val i = idx
                    if (i < 0 || i !in cur.bands.indices) return@detectDragGestures
                    ch.consume()
                    px += d.x; py += d.y
                    val b = cur.bands[i]
                    // Kolejność pasm zostaje: częstotliwość między sąsiadami.
                    val lo = cur.bands.getOrNull(i - 1)?.freq?.times(1.06f) ?: Dsp.F_MIN
                    val hi = cur.bands.getOrNull(i + 1)?.freq?.div(1.06f) ?: Dsp.F_MAX
                    val f = fOf(px).coerceIn(minOf(lo, hi), maxOf(lo, hi))
                    val g = if (Dsp.hasGain(b.type)) (dOf(py) * 2).roundToInt() / 2f else b.gain
                    Dsp.setBand(i, b.copy(freq = if (f >= 1000) (f / 10).roundToInt() * 10f else f.roundToInt().toFloat(), gain = g.coerceIn(-Dsp.MAX_GAIN, Dsp.MAX_GAIN)))
                }
            }
            .pointerInput(Unit) {
                val inset = 14.dp.toPx(); val padY = 14.dp.toPx()
                fun xOf(f: Float) = inset + ln(f / Dsp.F_MIN) / ln(Dsp.F_MAX / Dsp.F_MIN) * (size.width - 2 * inset)
                fun yOf(d: Float) = padY + (rng - d) / (2 * rng) * (size.height - 2 * padY)
                fun nearest(o: Offset): Int {
                    val bands = cur.bands
                    val byDist = bands.indices.minByOrNull { hypot(xOf(bands[it].freq) - o.x, yOf(bands[it].gain) - o.y) } ?: return -1
                    if (hypot(xOf(bands[byDist].freq) - o.x, yOf(bands[byDist].gain) - o.y) < 32.dp.toPx()) return byDist
                    return bands.indices.minByOrNull { abs(xOf(bands[it].freq) - o.x) } ?: -1
                }
                detectTapGestures(
                    onTap = { o -> val i = nearest(o); select(if (i == sel) -1 else i) },
                    onDoubleTap = { o ->
                        val i = nearest(o)
                        if (i >= 0 && cur.enabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            Dsp.checkpoint(); Dsp.setBand(i, cur.bands[i].copy(gain = 0f)); select(i)
                        }
                    },
                )
            },
    ) {
        @Suppress("UNUSED_VARIABLE") val t = live.tick
        val inset = 14.dp.toPx(); val padY = 14.dp.toPx()
        val w = size.width; val h = size.height
        fun xOf(f: Float) = inset + ln(f / Dsp.F_MIN) / ln(Dsp.F_MAX / Dsp.F_MIN) * (w - 2 * inset)
        fun y(d: Float) = padY + (range - d.coerceIn(-range - 3, range + 3)) / (2 * range) * (h - 2 * padY)
        val alpha = if (s.enabled) 1f else 0.35f

        // Widmo na żywo (40 Hz – 16 kHz, jak w procesorze widma) – tło, bez akcentu.
        val n = live.bands.size
        if (n > 1) {
            val spec = Path().apply {
                moveTo(xOf(40f), h)
                for (i in 0 until n) lineTo(xOf(40f * (16000f / 40f).pow(i / (n - 1f))), h - live.bands[i] * (h - padY) * 0.85f)
                lineTo(xOf(16000f), h); close()
            }
            drawPath(spec, fg.copy(alpha = 0.06f))
        }

        // Siatka: dB co 3/6 dB, częstotliwości dekadowo.
        val step = if (range <= 6f) 3f else 6f
        var d = -(range - range % step)
        while (d <= range + 0.01f) {
            val zero = abs(d) < 0.01f
            drawLine(fg.copy(alpha = if (zero) 0.22f else 0.06f), Offset(0f, y(d)), Offset(w, y(d)), 1.dp.toPx())
            if (!zero) drawText(measurer, (if (d > 0) "+" else "−") + abs(d).roundToInt(), Offset(4.dp.toPx(), y(d) - 12.dp.toPx()), axisStyle)
            d += step
        }
        for (f in listOf(50f, 100f, 200f, 500f, 1000f, 2000f, 5000f, 10000f)) {
            val x = xOf(f)
            drawLine(fg.copy(alpha = 0.06f), Offset(x, padY), Offset(x, h - padY), 1.dp.toPx())
            drawText(measurer, freqLabel(f), Offset(x + 3.dp.toPx(), h - padY + 1.dp.toPx()), axisStyle)
        }

        fun path(c: FloatArray) = Path().apply {
            for (i in c.indices) {
                val px = xOf(Dsp.F_MIN * (Dsp.F_MAX / Dsp.F_MIN).pow(i / (c.size - 1f)))
                if (i == 0) moveTo(px, y(c[i])) else lineTo(px, y(c[i]))
            }
        }
        val x0 = xOf(Dsp.F_MIN); val x1 = xOf(Dsp.F_MAX)

        // Całkowite wzmocnienie toru (przedwzmacniacz + zapas).
        if (abs(outGain) > 0.05f) {
            val yg = y(outGain)
            drawLine(
                accent.copy(alpha = 0.55f * alpha), Offset(x0, yg), Offset(x1, yg), 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
            )
            val m = measurer.measure("${dbText(outGain)} dB", axisStyle.copy(color = accent.copy(alpha = 0.9f * alpha)))
            drawText(m, topLeft = Offset(w - m.size.width - 4.dp.toPx(), yg + 2.dp.toPx()))
        }

        bandCurve?.let {
            val p = path(it)
            drawPath(Path().apply { addPath(p); lineTo(x1, y(0f)); lineTo(x0, y(0f)); close() }, accent.copy(alpha = 0.10f * alpha))
            drawPath(p, accent.copy(alpha = 0.55f * alpha), style = Stroke(1.2.dp.toPx()))
        }

        val total = path(curve)
        drawPath(Path().apply { addPath(total); lineTo(x1, y(0f)); lineTo(x0, y(0f)); close() }, accent.copy(alpha = 0.16f * alpha))
        drawPath(total, accent.copy(alpha = alpha), style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        s.bands.forEachIndexed { i, b -> node(Offset(xOf(b.freq), y(b.gain)), i == selected, accent, fg, bg, alpha) }

        // Dymek z wartościami zaznaczonego pasma (jak dymki podpowiedzi w aplikacji: Fg z tekstem Bg).
        s.bands.getOrNull(selected)?.let { b ->
            val txt = if (Dsp.hasGain(b.type)) "${freqText(b.freq)}  ${dbText(b.gain)} dB" else freqText(b.freq)
            val m = measurer.measure(txt, tipStyle)
            val cx = xOf(b.freq); val cy = y(b.gain)
            val pw = m.size.width + 14.dp.toPx(); val ph = m.size.height + 6.dp.toPx()
            val tx = (cx - pw / 2).coerceIn(2.dp.toPx(), w - pw - 2.dp.toPx())
            val above = cy - 18.dp.toPx() - ph
            val ty = if (above > 0f) above else cy + 18.dp.toPx()
            drawRoundRect(fg.copy(alpha = alpha), Offset(tx, ty), Size(pw, ph), CornerRadius(ph / 2))
            drawText(m, topLeft = Offset(tx + 7.dp.toPx(), ty + 3.dp.toPx()))
        }
    }
}

private fun DrawScope.node(c: Offset, sel: Boolean, accent: Color, fg: Color, bg: Color, alpha: Float) {
    if (sel) drawCircle(accent.copy(alpha = 0.22f * alpha), 15.dp.toPx(), c)
    drawCircle(bg.copy(alpha = 0.9f), (if (sel) 8f else 6.5f).dp.toPx(), c)
    drawCircle((if (sel) accent else fg).copy(alpha = alpha), (if (sel) 6f else 4.5f).dp.toPx(), c)
}

/** Szerokość kolumny fadera przy wielu pasmach (rząd przewijany). */
val FaderWidth: Dp = 36.dp
