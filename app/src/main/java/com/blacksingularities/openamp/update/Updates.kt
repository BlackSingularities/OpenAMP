package com.blacksingularities.openamp.update

import com.blacksingularities.openamp.BuildConfig
import com.blacksingularities.openamp.i18n.tr
import org.json.JSONObject

val UPDATE_API = "https://redzikowski.com/api/github/repos/${BuildConfig.UPDATE_REPO}/releases/latest"

data class ReleaseInfo(
    val version: String,
    val notes: String,
    val assetUrl: String,
    val sizeBytes: Long,
    val pageUrl: String,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: ReleaseInfo) : UpdateState
    data class Downloading(val info: ReleaseInfo, val progress: Float) : UpdateState
    data class Ready(val info: ReleaseInfo, val file: java.io.File) : UpdateState
    data class Installing(val info: ReleaseInfo) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/** Najnowsze wydanie z odpowiedzi GitHub API (plik .apk z załączników). */
fun parseRelease(json: String): ReleaseInfo {
    val o = JSONObject(json)
    val assets = o.optJSONArray("assets")
    var url = ""
    var size = 0L
    if (assets != null) {
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk")) {
                url = a.optString("browser_download_url")
                size = a.optLong("size")
                break
            }
        }
    }
    if (url.isEmpty()) error(tr("Wydanie nie zawiera pliku APK"))
    return ReleaseInfo(
        version = o.optString("tag_name").removePrefix("v"),
        notes = o.optString("body").substringBefore("\n---").trim(),
        assetUrl = url,
        sizeBytes = size,
        pageUrl = o.optString("html_url"),
    )
}

/** Porównanie numeryczne wersji: „1.1.12” > „1.1.9”; przyrostki typu „-dev” są pomijane. */
fun isNewer(remote: String, local: String): Boolean {
    fun parts(v: String) = v.split('.', '-').mapNotNull { it.toIntOrNull() }
    val r = parts(remote)
    val l = parts(local)
    for (i in 0 until maxOf(r.size, l.size)) {
        val a = r.getOrElse(i) { 0 }
        val b = l.getOrElse(i) { 0 }
        if (a != b) return a > b
    }
    return false
}
