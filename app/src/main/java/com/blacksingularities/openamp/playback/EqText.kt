package com.blacksingularities.openamp.playback

import java.util.Locale
import kotlin.math.abs

/**
 * Korektor jako tekst – format Equalizer APO, w którym AutoEQ publikuje korekcje słuchawek:
 * `Preamp: -6.2 dB` i wiersze `Filter 1: ON PK Fc 105 Hz Gain 3.0 dB Q 0.70`
 * (PK, LSC/LS, HSC/HS, LP, HP, NO). Rozumie też `GraphicEQ: 20 -3.1; 21 -3.0; …` –
 * taka krzywa jest przeliczana na bieżącą liczbę pasm.
 */
object EqText {
    data class Parsed(val bands: List<EqBand>, val preamp: Float?)

    private val num = """([-+]?\d+(?:[.,]\d+)?)"""
    private val preampRe = Regex("""preamp\s*:\s*$num\s*db""", RegexOption.IGNORE_CASE)
    private val filterRe = Regex(
        """filter\s*\d*\s*:\s*(on|off)\s+([a-z]+)\s+fc\s+$num\s*hz(?:\s+gain\s+$num\s*db)?(?:\s+q\s+$num)?""",
        RegexOption.IGNORE_CASE,
    )
    private val graphicRe = Regex("""graphiceq\s*:\s*(.+)""", RegexOption.IGNORE_CASE)

    private fun f(s: String) = s.replace(',', '.').toFloat()

    private fun typeOf(code: String): FilterType? = when (code.uppercase(Locale.ROOT)) {
        "PK", "PEQ", "PEAK" -> FilterType.PEAK
        "LS", "LSC", "LSQ", "LOWSHELF" -> FilterType.LOW_SHELF
        "HS", "HSC", "HSQ", "HIGHSHELF" -> FilterType.HIGH_SHELF
        "LP", "LPQ", "LOWPASS" -> FilterType.LOW_PASS
        "HP", "HPQ", "HIGHPASS" -> FilterType.HIGH_PASS
        "NO", "NOTCH" -> FilterType.NOTCH
        else -> null
    }

    /** Odczyt tekstu; null – nie ma w nim ani jednego filtra. [bandCount] – liczba pasm dla krzywej GraphicEQ. */
    fun parse(text: String, bandCount: Int = 10): Parsed? {
        val preamp = preampRe.find(text)?.groupValues?.get(1)?.let(::f)
        val bands = filterRe.findAll(text).mapNotNull { m ->
            val (on, code, fc, gain, q) = m.destructured
            if (!on.equals("on", ignoreCase = true)) return@mapNotNull null
            val type = typeOf(code) ?: return@mapNotNull null
            val defQ = if (type == FilterType.PEAK) 1.41f else Dsp.SHELF_Q
            EqBand(f(fc), if (gain.isBlank() || !Dsp.hasGain(type)) 0f else f(gain), if (q.isBlank()) defQ else f(q), type)
        }.toList()
        if (bands.isNotEmpty()) return Parsed(bands.sortedBy { it.freq }.take(Dsp.MAX_BANDS), preamp)

        val graphic = graphicRe.find(text)?.groupValues?.get(1) ?: return null
        val points = graphic.split(';').mapNotNull { p ->
            val parts = p.trim().split(Regex("""\s+"""))
            if (parts.size < 2) null else runCatching { f(parts[0]) to f(parts[1]) }.getOrNull()
        }.filter { it.first > 0f }.sortedBy { it.first }
        if (points.size < 2) return null
        val n = bandCount.coerceIn(5, Dsp.MAX_BANDS)
        val fs = points.map { it.first }; val gs = points.map { it.second }
        val curve = Dsp.freqsFor(n).map { fr -> EqBand(fr, Math.round(Dsp.curveAt(fs, gs, fr) * 2) / 2f, Dsp.qFor(n)) }
        return Parsed(curve, preamp)
    }

    private fun fmt(v: Float, digits: Int) = String.format(Locale.ROOT, "%.${digits}f", v)

    private fun code(t: FilterType) = when (t) {
        FilterType.PEAK -> "PK"
        FilterType.LOW_SHELF -> "LSC"
        FilterType.HIGH_SHELF -> "HSC"
        FilterType.LOW_PASS -> "LP"
        FilterType.HIGH_PASS -> "HP"
        FilterType.NOTCH -> "NO"
    }

    /** Bieżące ustawienia w formacie Equalizer APO (półki basów i sopranów jako dodatkowe filtry). */
    fun export(s: DspState): String {
        val all = buildList {
            addAll(s.bands.filter { Dsp.isActive(it) })
            if (abs(s.bass) > 0.05f) add(EqBand(Dsp.BASS_FREQ, s.bass, Dsp.SHELF_Q, FilterType.LOW_SHELF))
            if (abs(s.treble) > 0.05f) add(EqBand(Dsp.TREBLE_FREQ, s.treble, Dsp.SHELF_Q, FilterType.HIGH_SHELF))
        }.sortedBy { it.freq }
        return buildString {
            append("Preamp: ").append(fmt(Dsp.outputGainDb(s), 1)).append(" dB\n")
            all.forEachIndexed { i, b ->
                append("Filter ").append(i + 1).append(": ON ").append(code(b.type)).append(" Fc ").append(Math.round(b.freq)).append(" Hz")
                if (Dsp.hasGain(b.type)) append(" Gain ").append(fmt(b.gain, 1)).append(" dB")
                append(" Q ").append(fmt(b.q, 2)).append('\n')
            }
        }
    }
}
