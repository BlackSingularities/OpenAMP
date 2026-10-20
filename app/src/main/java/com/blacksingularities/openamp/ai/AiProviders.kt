package com.blacksingularities.openamp.ai

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Sposób rozmowy z dostawcą: natywne API Anthropic albo API zgodne z OpenAI (/chat/completions). */
enum class AiApi { ANTHROPIC, OPENAI }

/**
 * Dostawcy AI do analizy biblioteki. Prawie wszyscy udostępniają API zgodne z OpenAI, więc
 * wystarczy adres bazowy; adres można też zmienić w ustawieniach (np. własny serwer Ollama w sieci).
 * [fallbackModels] – lista na wypadek, gdy dostawca nie podaje modeli przez /models.
 */
enum class AiProvider(
    val label: String,
    val baseUrl: String,
    val api: AiApi = AiApi.OPENAI,
    val needsKey: Boolean = true,
    val fallbackModels: List<String> = emptyList(),
) {
    ANTHROPIC("Anthropic (Claude)", "https://api.anthropic.com/v1", AiApi.ANTHROPIC, fallbackModels = listOf("claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-5-5")),
    OPENAI("OpenAI", "https://api.openai.com/v1"),
    GOOGLE("Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai"),
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1"),
    MISTRAL("Mistral", "https://api.mistral.ai/v1"),
    XAI("xAI (Grok)", "https://api.x.ai/v1"),
    DEEPSEEK("DeepSeek", "https://api.deepseek.com/v1", fallbackModels = listOf("deepseek-chat", "deepseek-reasoner")),
    GROQ("Groq", "https://api.groq.com/openai/v1"),
    CEREBRAS("Cerebras", "https://api.cerebras.ai/v1"),
    TOGETHER("Together AI", "https://api.together.xyz/v1"),
    FIREWORKS("Fireworks AI", "https://api.fireworks.ai/inference/v1"),
    DEEPINFRA("DeepInfra", "https://api.deepinfra.com/v1/openai"),
    PERPLEXITY("Perplexity", "https://api.perplexity.ai", fallbackModels = listOf("sonar", "sonar-pro", "sonar-reasoning-pro")),
    COHERE("Cohere", "https://api.cohere.ai/compatibility/v1"),
    NVIDIA("NVIDIA NIM", "https://integrate.api.nvidia.com/v1"),
    HUGGINGFACE("Hugging Face", "https://router.huggingface.co/v1"),
    MOONSHOT("Moonshot (Kimi)", "https://api.moonshot.ai/v1"),
    QWEN("Alibaba Qwen", "https://dashscope-intl.aliyuncs.com/compatible-mode/v1"),
    ZAI("Z.ai (GLM)", "https://api.z.ai/api/paas/v4"),
    OLLAMA("Ollama", "http://192.168.1.10:11434/v1", needsKey = false),
    LMSTUDIO("LM Studio", "http://192.168.1.10:1234/v1", needsKey = false),
    CUSTOM("Własny (zgodny z OpenAI)", "", needsKey = false),
}

/** Zapytania HTTP do dostawców – bez dodatkowych bibliotek, na zwykłym HttpURLConnection. */
object AiClient {

    private fun request(url: String, provider: AiProvider, key: String, body: String?, readTimeoutMs: Int): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 20_000
            c.readTimeout = readTimeoutMs
            c.setRequestProperty("Accept", "application/json")
            if (provider.api == AiApi.ANTHROPIC) {
                c.setRequestProperty("x-api-key", key)
                c.setRequestProperty("anthropic-version", "2023-06-01")
            } else if (key.isNotBlank()) {
                c.setRequestProperty("Authorization", "Bearer $key")
            }
            if (provider == AiProvider.OPENROUTER) {
                c.setRequestProperty("HTTP-Referer", "https://github.com/BlackSingularities/OpenAMP")
                c.setRequestProperty("X-Title", "OpenAMP")
            }
            if (body != null) {
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw HttpError(code, errorMessage(text) ?: "HTTP $code")
            return text
        } finally {
            c.disconnect()
        }
    }

    class HttpError(val code: Int, message: String) : IOException(message)

    private fun errorMessage(text: String): String? = runCatching {
        val o = JSONObject(text)
        val e = o.opt("error")
        when (e) {
            is JSONObject -> e.optString("message").ifBlank { null }
            is String -> e
            else -> o.optString("message").ifBlank { null }
        }
    }.getOrNull() ?: text.take(200).ifBlank { null }

    /** Modele dostępne u dostawcy (z /models), posortowane; pusta lista – gdy dostawca ich nie podaje. */
    fun listModels(provider: AiProvider, baseUrl: String, key: String): List<String> {
        val base = baseUrl.trimEnd('/')
        if (base.isBlank()) error("Brak adresu serwera")
        val text = request("$base/models" + if (provider == AiProvider.ANTHROPIC) "?limit=1000" else "", provider, key, null, 30_000)
        val trimmed = text.trim()
        // Większość: {"data":[{"id":…}]}, Together – sama tablica, Gemini czasem {"models":[{"name":"models/…"}]}.
        val arr: JSONArray = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            else -> JSONObject(trimmed).let { it.optJSONArray("data") ?: it.optJSONArray("models") ?: JSONArray() }
        }
        val ids = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull arr.optString(i).ifBlank { null }
            (o.optString("id").ifBlank { o.optString("name") }).removePrefix("models/").ifBlank { null }
        }
        // Modele niebędące czatem (obrazy, mowa, wektory) tylko by przeszkadzały.
        val skip = Regex("embed|whisper|tts|dall-e|image|moderation|rerank|audio|transcribe|realtime|vision-only|guard", RegexOption.IGNORE_CASE)
        return ids.filterNot { skip.containsMatchIn(it) }.distinct().sorted()
    }

    /**
     * Rozmowa przez API zgodne z OpenAI: prośba o JSON. Gdy serwer nie zna `response_format`,
     * pytanie jest powtarzane bez niego (opis formatu i tak jest w poleceniu).
     */
    fun chatJson(provider: AiProvider, baseUrl: String, key: String, model: String, system: String, user: String): String {
        val base = baseUrl.trimEnd('/')
        if (base.isBlank()) error("Brak adresu serwera")
        fun body(json: Boolean) = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().put(JSONObject().put("role", "system").put("content", system)).put(JSONObject().put("role", "user").put("content", user)))
            if (json) put("response_format", JSONObject().put("type", "json_object"))
        }.toString()
        val text = try {
            request("$base/chat/completions", provider, key, body(true), 600_000)
        } catch (e: HttpError) {
            if (e.code in 400..422) request("$base/chat/completions", provider, key, body(false), 600_000) else throw e
        }
        val o = JSONObject(text)
        val choice = o.optJSONArray("choices")?.optJSONObject(0) ?: error(errorMessage(text) ?: "Pusta odpowiedź")
        if (choice.optString("finish_reason") == "length") error("Odpowiedź została ucięta – spróbuj większego modelu")
        return choice.optJSONObject("message")?.optString("content").orEmpty()
    }

    /** Wycina obiekt JSON z odpowiedzi (modele lubią dodać ```json i komentarz). */
    fun extractJson(text: String): String {
        val a = text.indexOf('{')
        val b = text.lastIndexOf('}')
        if (a < 0 || b <= a) error("Odpowiedź bez JSON")
        return text.substring(a, b + 1)
    }
}
