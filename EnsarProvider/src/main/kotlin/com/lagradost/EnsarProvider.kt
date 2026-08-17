
package com.lagradost



import com.lagradost.cloudstream3.*

import com.lagradost.cloudstream3.utils.ExtractorLink

import com.lagradost.cloudstream3.utils.ExtractorLinkType

import com.lagradost.cloudstream3.utils.newExtractorLink



class EnsarProvider : MainAPI() {



    override var mainUrl = "https://www.porndish.com"

    override var name = "Ensar"

    override var lang = "en"



    override val hasMainPage = true

    override val hasQuickSearch = true

    override val hasDownloadSupport = true



    override val supportedTypes = setOf(

        TvType.Movie

    )



    override val mainPage = mainPageOf(

        "$mainUrl/" to "Latest",

        "$mainUrl/page/%d/" to "Latest"

    )



    override suspend fun getMainPage(

    page: Int,

    request: MainPageRequest

): HomePageResponse {



    val url = if (page == 1) {

        "$mainUrl/"

    } else {

        "$mainUrl/page/$page/"

    }



    println("ENSAR DEBUG URL: $url")
    val document = app.get(url).document
    println("ENSAR DEBUG TITLE: ${document.title()}")
    println("ENSAR DEBUG HTML SIZE: ${document.html().length}")
    println("ENSAR DEBUG CLOUDFLARE: ${document.html().contains("Just a moment", ignoreCase = true)}")
    println("ENSAR DEBUG PORN LINKS: ${document.select("a[href*='/porn/']").size}")



    val items = document

        .select("a[href*=\"/porn/\"]")

        .mapNotNull { element ->



            val href = element.attr("href")

                .trim()

                .takeIf {

                    it.matches(

                        Regex("""${Regex.escape(mainUrl)}/porn/[^/?#]+/?""")

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



            val poster = element.select("img")

                .attr("src")

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

        listOf(

            HomePageList("Latest", items)

        ),

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

        val page = app.get(
            data,
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0",
                "Referer" to mainUrl
            )
        ).document

        val iframeUrl = page.select("iframe[src], iframe[data-src-no-ap]")
            .firstOrNull()
            ?.let {
                it.attr("src").takeIf { src -> src.isNotBlank() }
                    ?: it.attr("data-src-no-ap").takeIf { src -> src.isNotBlank() }
            }
            ?: return false

        val filecode = iframeUrl
            .substringAfterLast("/")
            .substringBefore("?")
            .takeIf { it.isNotBlank() }
            ?: return false

        val response = app.post(
            "https://vidara.to/api/stream",
            headers = mapOf(
                "Accept" to "application/json",
                "Content-Type" to "application/json",
                "Origin" to "https://vidara.to",
                "Referer" to "https://vidara.to/",
                "User-Agent" to "Mozilla/5.0"
            ),
            json = mapOf(
                "filecode" to filecode,
                "device" to "web"
            )
        ).parsedSafe<VidaraStreamResponse>() ?: return false

        val videoUrl = response.streaming_url
            .takeIf { it.startsWith("http") }
            ?: return false

        newExtractorLink(
            source = "Vidara",
            name = "Vidara",
            url = videoUrl,
            type = ExtractorLinkType.M3U8
        ) {
            this.referer = "https://vidara.to/"
            }

        return true
    }



    data class VidaraStreamResponse(

        val streaming_url: String = "",

        val filecode: String = "",

        val title: String? = null

    )

}

