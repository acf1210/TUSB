package com.opentonex.controller.update

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Resposta da API do GitHub que nao tem o formato de uma release do TUSB. */
class ReleaseParseException(message: String) : Exception(message)

/** Ultima release publicada do TUSB. [apkUrl] e null se a release nao trouxer o APK. */
data class AvailableUpdate(
    val version: String,
    val releaseUrl: String,
    val apkUrl: String?,
    val notes: String
)

/** Compara versoes `1.2.3` (com ou sem `v`), parte a parte; partes ausentes valem 0. */
object AppVersion {
    fun isNewer(candidate: String, current: String): Boolean {
        val a = parts(candidate) ?: return false
        val b = parts(current) ?: return true
        for (i in 0 until maxOf(a.size, b.size)) {
            val diff = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (diff != 0) return diff > 0
        }
        return false
    }

    /** null quando nao e uma versao numerica (ex.: tag "nightly"). */
    fun parts(version: String): List<Int>? =
        version.trim().removePrefix("v").removePrefix("V").substringBefore('-')
            .split('.')
            .map { it.toIntOrNull() ?: return null }
            .takeIf { it.isNotEmpty() }
}

/**
 * Le a resposta de `releases/latest` da API do GitHub. So aceita links do proprio repositorio
 * do TUSB: o APK oferecido ao usuario nunca vem de outro endereco.
 */
object ReleaseParser {
    const val REPOSITORY_URL = "https://github.com/acf1210/TUSB"
    private const val RELEASES_PREFIX = "$REPOSITORY_URL/releases/"
    private const val DOWNLOAD_PREFIX = "$REPOSITORY_URL/releases/download/"
    private const val MAX_NOTES_LENGTH = 1200

    fun parse(json: String, language: String): AvailableUpdate {
        val release = runCatching { JSONObject(json) }.getOrElse {
            throw ReleaseParseException("resposta do GitHub nao e JSON")
        }
        val tag = release.optString("tag_name")
        if (AppVersion.parts(tag) == null) throw ReleaseParseException("tag sem versao: '$tag'")
        val releaseUrl = release.optString("html_url")
        if (!releaseUrl.startsWith(RELEASES_PREFIX)) throw ReleaseParseException("link de release inesperado")
        val assets = release.optJSONArray("assets")
        val apkUrl = (0 until (assets?.length() ?: 0))
            .mapNotNull { assets?.optJSONObject(it)?.optString("browser_download_url") }
            .firstOrNull { it.startsWith(DOWNLOAD_PREFIX) && it.endsWith(".apk") }
        return AvailableUpdate(
            version = tag.removePrefix("v"),
            releaseUrl = releaseUrl,
            apkUrl = apkUrl,
            notes = notesFor(release.optString("body"), language)
        )
    }

    /**
     * As notas das releases tem uma secao por idioma (`## Português Brasil`, `## English US`,
     * `## Español`). Mostra so a do idioma do aparelho; sem ela, a de ingles.
     */
    fun notesFor(body: String, language: String): String {
        val sections = body.replace("\r\n", "\n").split(Regex("(?m)^## ")).drop(1)
            .associate { section -> section.substringBefore('\n').trim() to section.substringAfter('\n').trim() }
        val heading = when (language) {
            "pt" -> "Português"
            "es" -> "Español"
            else -> "English"
        }
        val notes = sections.entries.firstOrNull { it.key.startsWith(heading) }?.value
            ?: sections.entries.firstOrNull { it.key.startsWith("English") }?.value
            ?: body.trim()
        return if (notes.length <= MAX_NOTES_LENGTH) notes else notes.take(MAX_NOTES_LENGTH).trimEnd() + "…"
    }
}

/** Consulta a ultima release publicada. [fetch] e injetavel para os testes. */
class UpdateChecker(private val fetch: suspend (String) -> String = ::httpGet) {
    suspend fun latest(language: String): AvailableUpdate = ReleaseParser.parse(fetch(LATEST_RELEASE_API), language)

    companion object {
        const val LATEST_RELEASE_API = "https://api.github.com/repos/acf1210/TUSB/releases/latest"
        private const val TIMEOUT_MS = 10_000
        private const val MAX_RESPONSE_BYTES = 512 * 1024

        suspend fun httpGet(url: String): String = withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("User-Agent", "TUSB-Android")
                val code = connection.responseCode
                if (code != HttpURLConnection.HTTP_OK) throw java.io.IOException("GitHub respondeu HTTP $code")
                val bytes = connection.inputStream.use { it.readNBytesCompat(MAX_RESPONSE_BYTES) }
                String(bytes, Charsets.UTF_8)
            } finally {
                connection.disconnect()
            }
        }

        private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (out.size() < limit) {
                val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
                if (read < 0) break
                out.write(buffer, 0, read)
            }
            return out.toByteArray()
        }
    }
}

/** Checagem automatica no maximo a cada [intervalMs], para nao consumir a cota da API. */
fun isAutoCheckDue(lastCheckAt: Long, now: Long, intervalMs: Long = AUTO_CHECK_INTERVAL_MS): Boolean =
    lastCheckAt <= 0L || now - lastCheckAt >= intervalMs || now < lastCheckAt

const val AUTO_CHECK_INTERVAL_MS = 12L * 60 * 60 * 1000
