package com.blacksingularities.openamp.lyrics

import android.content.Context
import com.blacksingularities.openamp.data.Track
import java.io.DataInputStream
import java.io.InputStream
import java.nio.charset.Charset

/** Odczyt tekstu zapisanego w tagach pliku – bez zewnętrznych bibliotek. */
object TagLyrics {
    private const val MAX_TAG = 16 * 1024 * 1024

    fun read(context: Context, track: Track): String? {
        val cr = context.contentResolver
        val head = cr.openInputStream(track.uri)?.use { s -> ByteArray(10).also { readFully(s, it) } } ?: return null
        return when {
            head[0] == 'I'.code.toByte() && head[1] == 'D'.code.toByte() && head[2] == '3'.code.toByte() ->
                cr.openInputStream(track.uri)?.use { id3(it) }
            head[0] == 'f'.code.toByte() && head[1] == 'L'.code.toByte() && head[2] == 'a'.code.toByte() && head[3] == 'C'.code.toByte() ->
                cr.openInputStream(track.uri)?.use { flac(it) }
            head[4] == 'f'.code.toByte() && head[5] == 't'.code.toByte() && head[6] == 'y'.code.toByte() && head[7] == 'p'.code.toByte() ->
                if (track.size in 1..80L * 1024 * 1024) cr.openInputStream(track.uri)?.use { mp4(it.readBytes()) } else null
            else -> null
        }
    }

    private fun readFully(s: InputStream, b: ByteArray): Int {
        var off = 0
        while (off < b.size) {
            val n = s.read(b, off, b.size - off)
            if (n < 0) break
            off += n
        }
        return off
    }

    // ---------- ID3v2 ----------

    private fun id3(s: InputStream): String? {
        val h = ByteArray(10)
        if (readFully(s, h) < 10) return null
        val major = h[3].toInt()
        val flags = h[5].toInt()
        val size = syncsafe(h, 6)
        if (size <= 0 || size > MAX_TAG) return null
        var tag = ByteArray(size)
        readFully(s, tag)
        if (major < 4 && flags and 0x80 != 0) tag = unsync(tag)
        var p = 0
        if (flags and 0x40 != 0 && p + 4 <= tag.size) {
            p += if (major >= 4) syncsafe(tag, 0) else (int32(tag, 0) + 4)
        }
        var uslt: String? = null
        var sylt: String? = null
        val idLen = if (major == 2) 3 else 4
        val hdrLen = if (major == 2) 6 else 10
        while (p + hdrLen <= tag.size) {
            val id = String(tag, p, idLen, Charsets.ISO_8859_1)
            if (id[0] == '\u0000') break
            val fsize = when (major) {
                2 -> ((tag[p + 3].toInt() and 0xFF) shl 16) or ((tag[p + 4].toInt() and 0xFF) shl 8) or (tag[p + 5].toInt() and 0xFF)
                4 -> syncsafe(tag, p + 4)
                else -> int32(tag, p + 4)
            }
            val start = p + hdrLen
            if (fsize <= 0 || start + fsize > tag.size) break
            var body = tag.copyOfRange(start, start + fsize)
            if (major == 4 && (tag[p + 9].toInt() and 0x02) != 0) body = unsync(body)
            when (id) {
                "USLT", "ULT" -> if (uslt == null) uslt = parseUslt(body)
                "SYLT", "SLT" -> if (sylt == null) sylt = parseSylt(body)
            }
            p = start + fsize
        }
        return sylt ?: uslt
    }

    private fun syncsafe(b: ByteArray, o: Int) =
        ((b[o].toInt() and 0x7F) shl 21) or ((b[o + 1].toInt() and 0x7F) shl 14) or ((b[o + 2].toInt() and 0x7F) shl 7) or (b[o + 3].toInt() and 0x7F)

    private fun int32(b: ByteArray, o: Int) =
        ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)

    private fun unsync(b: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream(b.size)
        var i = 0
        while (i < b.size) {
            out.write(b[i].toInt())
            if (b[i] == 0xFF.toByte() && i + 1 < b.size && b[i + 1] == 0.toByte()) i++
            i++
        }
        return out.toByteArray()
    }

    private fun charset(enc: Int): Charset = when (enc) {
        1 -> Charsets.UTF_16
        2 -> Charsets.UTF_16BE
        3 -> Charsets.UTF_8
        else -> Charsets.ISO_8859_1
    }

    /** Koniec napisu zakończonego zerem (dla UTF-16 – dwoma zerami na parzystej pozycji). */
    private fun termEnd(b: ByteArray, from: Int, enc: Int): Int {
        if (enc == 1 || enc == 2) {
            var i = from
            while (i + 1 < b.size) {
                if (b[i] == 0.toByte() && b[i + 1] == 0.toByte()) return i
                i += 2
            }
            return b.size
        }
        var i = from
        while (i < b.size && b[i] != 0.toByte()) i++
        return i
    }

    private fun termLen(enc: Int) = if (enc == 1 || enc == 2) 2 else 1

    private fun parseUslt(b: ByteArray): String? {
        if (b.size < 5) return null
        val enc = b[0].toInt()
        val descEnd = termEnd(b, 4, enc)
        val start = (descEnd + termLen(enc)).coerceAtMost(b.size)
        return String(b, start, b.size - start, charset(enc)).trimEnd('\u0000').takeIf { it.isNotBlank() }
    }

    /** SYLT → tekst w formacie LRC (tylko znaczniki w milisekundach). */
    private fun parseSylt(b: ByteArray): String? {
        if (b.size < 7) return null
        val enc = b[0].toInt()
        if (b[4].toInt() != 2) return null
        var p = (termEnd(b, 6, enc) + termLen(enc))
        val sb = StringBuilder()
        while (p < b.size) {
            val end = termEnd(b, p, enc)
            val text = String(b, p, end - p, charset(enc)).trim('\n', '\r')
            p = end + termLen(enc)
            if (p + 4 > b.size) break
            val ms = int32(b, p).toLong()
            p += 4
            val m = ms / 60000
            val sec = (ms % 60000) / 1000
            val cs = (ms % 1000) / 10
            sb.append(String.format(java.util.Locale.ROOT, "[%02d:%02d.%02d]%s\n", m, sec, cs, text))
        }
        return sb.toString().takeIf { it.isNotBlank() }
    }

    // ---------- FLAC (komentarze Vorbis) ----------

    private fun flac(s: InputStream): String? {
        val d = DataInputStream(s)
        val magic = ByteArray(4)
        d.readFully(magic)
        while (true) {
            val hdr = d.readUnsignedByte()
            val last = hdr and 0x80 != 0
            val type = hdr and 0x7F
            val len = (d.readUnsignedByte() shl 16) or (d.readUnsignedByte() shl 8) or d.readUnsignedByte()
            if (type == 4) {
                val block = ByteArray(len)
                d.readFully(block)
                return vorbis(block)
            }
            d.skipBytes(len)
            if (last) return null
        }
    }

    private fun le32(b: ByteArray, o: Int) =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)

    private fun vorbis(b: ByteArray): String? {
        var p = 0
        val vendor = le32(b, p); p += 4 + vendor
        val count = le32(b, p); p += 4
        var synced: String? = null
        var plain: String? = null
        repeat(count) {
            if (p + 4 > b.size) return@repeat
            val l = le32(b, p); p += 4
            if (l < 0 || p + l > b.size) return@repeat
            val c = String(b, p, l, Charsets.UTF_8); p += l
            val key = c.substringBefore('=').uppercase()
            val value = c.substringAfter('=')
            when (key) {
                "SYNCEDLYRICS" -> synced = value
                "LYRICS", "UNSYNCEDLYRICS" -> if (plain == null) plain = value
            }
        }
        return synced ?: plain
    }

    // ---------- MP4 / M4A ----------

    private fun mp4(b: ByteArray): String? {
        val key = byteArrayOf(0xA9.toByte(), 'l'.code.toByte(), 'y'.code.toByte(), 'r'.code.toByte())
        var i = 0
        while (i < b.size - 16) {
            if (b[i] == key[0] && b[i + 1] == key[1] && b[i + 2] == key[2] && b[i + 3] == key[3]) {
                val d = i + 4
                if (b[d + 4] == 'd'.code.toByte() && b[d + 5] == 'a'.code.toByte()) {
                    val size = int32(b, d)
                    val start = d + 16
                    val end = (d + size).coerceAtMost(b.size)
                    if (end > start) return String(b, start, end - start, Charsets.UTF_8)
                }
            }
            i++
        }
        return null
    }
}
