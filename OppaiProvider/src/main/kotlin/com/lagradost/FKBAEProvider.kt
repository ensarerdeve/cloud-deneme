package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class OppaiProvider : MainAPI() {

    override var mainUrl = "https://oppai.stream"
    override var name = "Ensar Stream"
    override var lang = "en"

    override val hasMainPage = true
    override val hasQuickSearch = true
    override val hasDownloadSupport = true

    override val supportedTypes = setOf(TvType.Movie)

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Latest",
        "$mainUrl/categories" to "Categories"
    )

    private fun Element.toVideo(): SearchResponse? {
        val href = attr("href").trim()
        if (!href.contains("/watch/")) return null

        val cover = selectFirst("img.cover")
            ?: selectFirst("img[data-src]")
            ?: selectFirst("img")

        val title = attr("title").ifBlank {
            cover?.attr("alt").orEmpty()
        }.ifBlank {
            text().trim()
        }

        if (title.isBlank()) return null

        val poster = cover?.let {
            it.attr("data-src").ifBlank { it.attr("src") }
        }?.takeIf { it.isNotBlank() }

        return newMovieSearchResponse(
            title,
            fixUrl(href),
            TvType.Movie
        ) {
            posterUrl = poster?.let { fixUrl(it) }
        }
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        if (request.data.trimEnd('/') == "$mainUrl/categories") {
            val categoryDocument = app.get(request.data).document

            val blockedTerms = listOf(
                "shota", "loli", "lolita",
                "schoolgirl", "schoolboy",
                "underage", "minor", "child", "teen"
            )

            val categories = categoryDocument
                .select("a.category-card[href*=/category/]")
                .mapNotNull { element ->
                    val href = element.attr("href").trim()
                    if (href.isBlank()) return@mapNotNull null

                    val cover = element.selectFirst("img.cover")
                    val label = cover?.attr("alt")
                        ?.ifBlank { element.text().trim() }
                        ?: element.text().trim()

                    if (label.isBlank()) return@mapNotNull null

                    val identity = "$href $label".lowercase()
                    if (blockedTerms.any { identity.contains(it) }) {
                        return@mapNotNull null
                    }

                    Pair(label, fixUrl(href))
                }
                .distinctBy { it.second }

            val semaphore = Semaphore(4)

            val rows = coroutineScope {
                categories.map { (categoryName, categoryUrl) ->
                    async {
                        semaphore.withPermit {
                            try {
                                val document = app.get(categoryUrl).document
                                val videos = document
                                    .select("a[href*=/watch/]")
                                    .mapNotNull { it.toVideo() }
                                    .distinctBy { it.url }

                                if (videos.isEmpty()) null
                                else HomePageList(categoryName, videos)
                            } catch (e: Exception) {
                                println("ENSAR CATEGORY ERROR $categoryName: ${e.message}")
                                null
                            }
                        }
                    }
                }.awaitAll().filterNotNull()
            }

            println("ENSAR CATEGORIES FOUND: ${categories.size}")
            println("ENSAR CATEGORY ROWS LOADED: ${rows.size}")

            return newHomePageResponse(rows, hasNext = false)
        }

        val url = if (page <= 1) {
            request.data
        } else {
            "${request.data}?page=$page"
        }

        val document = app.get(url).document

        val items = document.select("a[href*=/watch/]")
            .mapNotNull { it.toVideo() }
            .distinctBy { it.url }

        return newHomePageResponse(
            request.name,
            items,
            hasNext = items.isNotEmpty()
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get(
            "$mainUrl/search",
            params = mapOf("q" to query)
        ).document

        return document.select("a[href*=/watch/]")
            .mapNotNull { it.toVideo() }
            .distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val response = app.get(url)
        val document = response.document
        val html = response.text

        val jsonName = Regex(
            """"name"\s*:\s*"([^"]+)""""
        ).find(html)?.groupValues?.get(1)

        val title = jsonName
            ?: document.selectFirst("meta[property=og:title]")
                ?.attr("content")
            ?: document.title()

        val poster = document.selectFirst(
            "meta[property=og:image]"
        )?.attr("content")

        return newMovieLoadResponse(
            name = title,
            url = url,
            type = TvType.Movie,
            dataUrl = url
        ) {
            posterUrl = poster
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val response = app.get(data)
        val html = response.text
        val document = response.document

        fun getVariable(name: String): String? {
            return Regex("""var\s+$name\s*=\s*"([^"]+)"""")
                .find(html)?.groupValues?.get(1)
        }

        println("ENSAR DEBUG LOADLINKS ENTERED: $data")

        val folderRaw = getVariable("folder")
        val storageRaw = getVariable("storageBase")
        val episodeRaw = getVariable("ep")

        println("ENSAR DEBUG folderRaw=$folderRaw")
        println("ENSAR DEBUG storageRaw=$storageRaw")
        println("ENSAR DEBUG episodeRaw=$episodeRaw")

        val folder = folderRaw?.replace("\\/", "/") ?: run {
            println("ENSAR DEBUG FAILED: folder missing")
            return false
        }

        val storageBase = storageRaw?.replace("\\/", "/") ?: run {
            println("ENSAR DEBUG FAILED: storageBase missing")
            return false
        }

        val episode = episodeRaw ?: run {
            println("ENSAR DEBUG FAILED: ep missing")
            return false
        }

        val resolutionBlock = Regex(
            """resList\s*=\s*\[([^\]]*)]"""
        ).find(html)?.groupValues?.get(1)

        val resolutions = resolutionBlock?.let {
            Regex(""""([^"]+)"""").findAll(it)
                .map { match -> match.groupValues[1] }.toList()
        } ?: emptyList()

        if (resolutions.none {
            it.equals("1080", ignoreCase = true) ||
            it.equals("720", ignoreCase = true)
        }) {
            println("ENSAR DEBUG: Skipped - no 1080p or 720p")
            return false
        }

        println("ENSAR DEBUG HTML RESOLUTIONS: $resolutionBlock")
        println("ENSAR DEBUG PARSED RESOLUTIONS: $resolutions")

        val encodedFolder = folder.split("/").joinToString("/") {
            java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20")
        }

        val paddedEpisode = episode.toIntOrNull()
            ?.toString()?.padStart(2, '0') ?: return false

        val subtitle = document.selectFirst(
            "video#episode track[src][kind=subtitles]"
        )?.attr("src")

        if (!subtitle.isNullOrBlank()) {
            subtitleCallback(SubtitleFile("English", fixUrl(subtitle)))
        }

        var added = false

        for (res in resolutions) {
            val folderName = if (res == "4k") "4K" else "${res}p"
            val extension = if (res == "4k") "webm" else "mp4"

            val videoUrl =
                "${storageBase.trimEnd('/')}/$encodedFolder/$folderName/E$paddedEpisode.$extension"

                val qualityName = if (res == "4k") "4K" else "${res}p"

            callback(
                newExtractorLink(
                    source = name,
                    name = qualityName,
                    url = videoUrl,
                    type = ExtractorLinkType.VIDEO
                ) {
                    this.referer = data
                    this.quality = if (res == "4k") 2160 else res.toInt()
                }
            )
            added = true
        }

        return added
    }
}
