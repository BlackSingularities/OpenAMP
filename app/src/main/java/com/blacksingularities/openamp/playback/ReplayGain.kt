package com.blacksingularities.openamp.playback

import androidx.media3.common.Metadata
import androidx.media3.common.Tracks
import androidx.media3.extractor.metadata.id3.InternalFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment

/**
 * Odczyt znaczników ReplayGain z metadanych utworu: komentarze Vorbis (FLAC, OGG, Opus),
 * ramki TXXX z ID3 (MP3) i atomy iTunes „----” (M4A/ALAC).
 */
object ReplayGain {
    /** Wzmocnienia (utwór, album) w dB z bieżących ścieżek; null – brak znacznika. */
    fun read(tracks: Tracks): Pair<Float?, Float?> {
        var track: Float? = null
        var album: Float? = null
        for (group in tracks.groups) {
            if (group.type != androidx.media3.common.C.TRACK_TYPE_AUDIO) continue
            for (i in 0 until group.length) {
                val md: Metadata = group.getTrackFormat(i).metadata ?: continue
                for (k in 0 until md.length()) {
                    val (key, value) = entry(md.get(k)) ?: continue
                    when (key.lowercase()) {
                        "replaygain_track_gain" -> track = track ?: parseDb(value)
                        "replaygain_album_gain" -> album = album ?: parseDb(value)
                    }
                }
            }
        }
        return track to album
    }

    private fun entry(e: Metadata.Entry): Pair<String, String>? = when (e) {
        is VorbisComment -> e.key to e.value
        is TextInformationFrame -> e.description?.let { d -> e.values.firstOrNull()?.let { d to it } }
        is InternalFrame -> e.description to e.text
        else -> null
    }

    /** „-6.53 dB”, „+2,1 dB” → liczba; wartości spoza rozsądnego zakresu odrzucane. */
    fun parseDb(text: String): Float? =
        text.trim().removeSuffix("dB").removeSuffix("db").trim().replace(',', '.').toFloatOrNull()?.takeIf { it in -30f..30f }
}
