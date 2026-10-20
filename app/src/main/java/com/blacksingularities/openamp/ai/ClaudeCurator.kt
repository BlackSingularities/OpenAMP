package com.blacksingularities.openamp.ai

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.blacksingularities.openamp.data.Track
import com.blacksingularities.openamp.data.UserData
import org.json.JSONObject
import java.time.Duration

/**
 * Analiza biblioteki przez Claude: model dostaje zwięzłą listę utworów (numer, tytuł, wykonawca,
 * album, gatunek, rok, długość, odtworzenia, serduszko) i zwraca kategorie oraz miksy jako JSON
 * zgodny ze schematem (structured outputs). Odpowiedź jest strumieniowana – przy dużych
 * bibliotekach trwa to chwilę, a strumień nie wpada w limity czasu połączenia.
 */
object ClaudeCurator {
    /** Większe biblioteki: do modelu trafia ta część, resztę dopasowujemy po wykonawcy. */
    private const val MAX_TRACKS = 3000

    val MODELS = listOf("claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-5-5")

    private fun obj(vararg props: Pair<String, Any>, required: List<String>): Map<String, Any> = mapOf(
        "type" to "object",
        "additionalProperties" to false,
        "required" to required,
        "properties" to props.toMap(),
    )

    private fun group(nameKey: String, descKey: String): Map<String, Any> = mapOf(
        "type" to "array",
        "items" to obj(
            nameKey to mapOf("type" to "string"),
            descKey to mapOf("type" to "string"),
            "tracks" to mapOf("type" to "array", "items" to mapOf("type" to "integer")),
            required = listOf(nameKey, descKey, "tracks"),
        ),
    )

    private val schema: Map<String, Any> = obj(
        "categories" to group("name", "description"),
        "mixes" to group("title", "subtitle"),
        required = listOf("categories", "mixes"),
    )

    fun run(apiKey: String, model: String, all: List<Track>, data: UserData, english: Boolean): Curation {
        // Najpierw ulubione i najczęściej grane – to one najlepiej opisują gust.
        val ordered = all.sortedWith(
            compareByDescending<Track> { it.id in data.favorites }.thenByDescending { data.stats[it.id]?.count ?: 0 },
        )
        val sent = ordered.take(MAX_TRACKS)
        val listing = buildString {
            sent.forEachIndexed { i, t ->
                val plays = data.stats[t.id]?.count ?: 0
                append(i).append('|').append(t.title.clean()).append('|').append(t.artist.clean()).append('|')
                    .append(t.album.clean()).append('|').append(t.genre.clean()).append('|')
                    .append(if (t.year > 0) t.year.toString() else "").append('|')
                    .append((t.durationMs / 60_000.0).let { String.format(java.util.Locale.ROOT, "%.1f", it) }).append('|')
                    .append(plays).append('|').append(if (t.id in data.favorites) "1" else "")
                    .append('\n')
            }
        }
        val lang = if (english) "English" else "Polish"
        val system = """
            You are the music curator inside OpenAMP, an offline music player. You receive the user's library as lines
            "index|title|artist|album|genre|year|minutes|plays|favorite" (empty fields are unknown).
            Build two things, writing every name and description in $lang:
            1. categories – 8 to 20 meaningful groups that help browse this particular library: moods, energy,
               genres and sub-genres, eras, scenes, languages, occasions (focus, workout, evening…). Use your own knowledge
               of the artists and titles, not only the tags. Every track must belong to at least one category; a track may
               be in several. Names short (1–3 words), descriptions one short sentence.
            2. mixes – 6 to 10 playlists of 20–50 tracks each, ordered so they flow well (no same artist back to back),
               shaped by what the user actually plays and likes (plays, favorite) mixed with tracks worth rediscovering.
               Titles short and evocative, subtitle one short sentence.
            Refer to tracks only by their index. Use only indexes that appear in the list.
        """.trimIndent()

        val client = AnthropicOkHttpClient.builder()
            .apiKey(apiKey)
            .timeout(Duration.ofMinutes(10))
            .build()
        try {
            val builder = MessageCreateParams.builder()
                .model(model)
                .maxTokens(64000L)
                .system(system)
                .addUserMessage(listing)
                .outputConfig(
                    OutputConfig.builder()
                        .effort(OutputConfig.Effort.LOW)
                        .format(
                            JsonOutputFormat.builder()
                                .schema(JsonOutputFormat.Schema.builder().apply { schema.forEach { (k, v) -> putAdditionalProperty(k, JsonValue.from(v)) } }.build())
                                .build(),
                        )
                        .build(),
                )
            // Gdy zabezpieczenia odrzucą zapytanie, serwer sam spróbuje innego modelu (Haiku nie ma tej opcji).
            if (!model.contains("haiku")) {
                builder.putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                builder.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            }
            val text = StringBuilder()
            var stop: String? = null
            client.messages().createStreaming(builder.build()).use { stream ->
                stream.stream().forEach { event ->
                    event.contentBlockDelta().ifPresent { d -> d.delta().text().ifPresent { text.append(it.text()) } }
                    event.messageDelta().ifPresent { d -> d.delta().stopReason().ifPresent { stop = it.toString() } }
                }
            }
            if (stop == "refusal") error(if (english) "Claude declined this request" else "Claude odmówił wykonania analizy")
            if (stop == "max_tokens") error(if (english) "The answer was cut off – try a larger model" else "Odpowiedź została ucięta – spróbuj większego modelu")
            return parse(text.toString(), sent, all)
        } finally {
            client.close()
        }
    }

    private fun String.clean() = replace('|', '/').replace('\n', ' ').trim()

    private fun parse(json: String, sent: List<Track>, all: List<Track>): Curation {
        val o = JSONObject(json.trim())
        fun ids(a: org.json.JSONArray?): List<Long> =
            if (a == null) emptyList() else List(a.length()) { a.optInt(it, -1) }.mapNotNull { sent.getOrNull(it)?.id }.distinct()
        val cats = ArrayList<AiCategory>()
        o.optJSONArray("categories")?.let { a ->
            for (i in 0 until a.length()) {
                val c = a.getJSONObject(i)
                val list = ids(c.optJSONArray("tracks"))
                if (list.isNotEmpty()) cats += AiCategory("ai_cat_$i", c.optString("name").ifBlank { "#${i + 1}" }, c.optString("description"), list)
            }
        }
        // Utwory spoza wysłanej części: do kategorii, w których jest najwięcej utworów tego samego wykonawcy.
        val sentIds = sent.mapTo(HashSet()) { it.id }
        val rest = all.filter { it.id !in sentIds }
        if (rest.isNotEmpty() && cats.isNotEmpty()) {
            val artistOf = sent.associate { it.id to it.artist.lowercase() }
            val extra = HashMap<Int, MutableList<Long>>()
            rest.forEach { t ->
                val a = t.artist.lowercase()
                val best = cats.indices.maxByOrNull { ci -> cats[ci].trackIds.count { artistOf[it] == a } } ?: return@forEach
                if (cats[best].trackIds.any { artistOf[it] == a }) extra.getOrPut(best) { ArrayList() } += t.id
            }
            extra.forEach { (ci, list) -> cats[ci] = cats[ci].copy(trackIds = cats[ci].trackIds + list) }
        }
        val mixes = ArrayList<AiMix>()
        o.optJSONArray("mixes")?.let { a ->
            for (i in 0 until a.length()) {
                val m = a.getJSONObject(i)
                val list = ids(m.optJSONArray("tracks"))
                if (list.size >= 3) mixes += AiMix("ai_mix_$i", m.optString("title").ifBlank { "Mix ${i + 1}" }, m.optString("subtitle"), list)
            }
        }
        if (cats.isEmpty() && mixes.isEmpty()) error("Pusta odpowiedź")
        return Curation(categories = cats, mixes = mixes, source = CurationSource.CLAUDE)
    }
}
