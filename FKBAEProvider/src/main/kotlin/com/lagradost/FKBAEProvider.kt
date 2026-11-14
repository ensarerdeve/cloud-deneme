package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink

class FKBAEProvider : MainAPI() {

    override var mainUrl = "https://fkbae.to"
    override var name = "FKBAE"
    override var lang = "en"

    override val hasMainPage = true
    override val hasQuickSearch = true
    override val hasDownloadSupport = true

    override val supportedTypes = setOf(
        TvType.Movie
    )

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Latest",
        "$mainUrl/snapchat-nudes/" to "Nudes",
        "$mainUrl/leaked-snapchat/" to "Leaked",
        "$mainUrl/snapchat-sex/" to "Sex",
        "$mainUrl/dirty-snapchat/" to "Dirty",
        "$mainUrl/snapchat-fuck/" to "Fuck",
        "$mainUrl/snapchat-xxx/" to "XXX",
        "$mainUrl/premium-snapchat/" to "Premium",
        "$mainUrl/snapchat-boobs/" to "Boobs",
        "$mainUrl/snapchat-sluts/" to "Sluts",
        "$mainUrl/snapchat-sexy/" to "Sexy",
        "$mainUrl/snapchat-lesbians/" to "Lesbians",
        "$mainUrl/snapchat-masturbation/" to "Masturbation",
        "$mainUrl/snapchat-asian/" to "Asian",
        "$mainUrl/snapchat-pussy/" to "Pussy",
        "$mainUrl/snapchat-milf/" to "MILF",
        "$mainUrl/snapchat-ass/" to "Ass",
        "$mainUrl/snapchat-horny/" to "Horny",
        "$mainUrl/snapchat-cum/" to "Cum",
        "$mainUrl/snapchat-blowjob/" to "Blowjob",
        "$mainUrl/snapchat-anal/" to "Anal"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val baseUrl = request.data.trimEnd('/')

        val url = if (page <= 1) {
            "$baseUrl/"
        } else {
            "$baseUrl/page/$page/"
        }

        val document = app.get(url).document

        val items = document
            .select("a[href^=\"$mainUrl/\"]")
            .mapNotNull { element ->

                val href = element.attr("href")
                    .trim()
                    .takeIf {
                        it.matches(
                            Regex("""${Regex.escape(mainUrl)}/\d+/?""")
                        )
                    }
                    ?: return@mapNotNull null

                val title = element.attr("title")
                    .trim()
                    .ifBlank {
                        element.select("img").attr("alt").trim()
                    }
                    .ifBlank {
                        return@mapNotNull null
                    }

                val poster = element.select("img").attr("src")
                    .takeIf { it.isNotBlank() }

                newMovieSearchResponse(
                    name = title,
                    url = href,
                    type = TvType.Movie
                ) {
                    posterUrl = poster
                }
            }
            .distinctBy { it.url }

        return newHomePageResponse(
            request,
            items,
            hasNext = items.size >= 20
        )
    }

    override suspend fun load(url: String): LoadResponse {

        val document = app.get(url).document

        val title = document.title()
            .substringBefore(" - ")
            .trim()
            .ifBlank { "FKBAE Video" }

        val poster = document.select("meta[property=og:image]")
            .attr("content")
            .takeIf { it.isNotBlank() }

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

        val page = app.get(data).document

        val iframe = page.select("iframe[src], iframe[data-src-no-ap]")
            .firstOrNull()

        val iframeUrl = iframe?.attr("src")
            ?.takeIf { it.isNotBlank() }
            ?: iframe?.attr("data-src-no-ap")
                ?.takeIf { it.isNotBlank() }
            ?: return false

        val player = app.get(
            iframeUrl,
            referer = data
        ).text

        val videoUrl = Regex(
            """<source[^>]+src=["']([^"']+)["']"""
        ).find(player)?.groupValues?.get(1)
            ?: Regex(
                """file\s*:\s*["']([^"']+)["']"""
            ).find(player)?.groupValues?.get(1)
            ?: return false

        newExtractorLink(
            source = name,
            name = "Direct",
            url = videoUrl,
            type = if (videoUrl.contains(".m3u8")) {
                ExtractorLinkType.M3U8
            } else {
                ExtractorLinkType.VIDEO
            }
        ) {
            this.referer = iframeUrl
            this.quality = 720
            callback(this)
        }

        return true
    }
}
