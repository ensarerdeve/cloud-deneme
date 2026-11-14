package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element

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
        "$mainUrl/category/uncensored" to "Uncensored Fight",
        "$mainUrl/category/rap" to "Rap Music",
        "$mainUrl/category/incect" to "Incects",
        "$mainUrl/category/big" to "Big Plays",
        "$mainUrl/category/shot" to "One Shot"
    )

    private fun Element.toVideo(): SearchResponse? {
        val href = attr("href").trim()
        if (!href.contains("/watch/")) return null

        val title = attr("title").ifBlank {
            selectFirst("img")?.attr("alt").orEmpty()
        }.ifBlank {
            text().trim()
        }

        if (title.isBlank()) return null

        val poster = selectFirst("img")?.let {
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
        val url = if (page <= 1) {
            request.data
        } else {
            "${
                request.data
            }?page=$page"
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

        val videoUrl = Regex(
            """"contentUrl"\s*:\s*"([^"]+)""""
        ).find(html)?.groupValues?.get(1)
            ?: return false

        val subtitle = document.selectFirst(
            "video#episode track[src][kind=subtitles]"
        )?.attr("src")

        if (!subtitle.isNullOrBlank()) {
            subtitleCallback(
                SubtitleFile(
                    "English",
                    fixUrl(subtitle)
                )
            )
        }

        callback(
            newExtractorLink(
                source = name,
                name = "Direct WebM",
                url = videoUrl,
                type = ExtractorLinkType.VIDEO
            ) {
                this.referer = data
                this.quality = 1080
            }
        )

        return true
    }
}
