
package com.lagradost



import com.lagradost.cloudstream3.*

import com.lagradost.cloudstream3.utils.ExtractorLink



class FKBAEProvider : MainAPI() {



    override var mainUrl = "https://fkbae.to"

    override var name = "FKBAE"

    override var lang = "en"

    override val hasMainPage = false

    override val hasQuickSearch = false

    override val hasDownloadSupport = true



    override val supportedTypes = setOf(

        TvType.Movie

    )



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



        if (iframeUrl == null) return false



        val player = app.get(iframeUrl).text



        val videoUrl = Regex(

            """file\s*:\s*["']([^"']+)["']"""

        ).find(player)?.groupValues?.get(1)



        if (videoUrl == null) return false

        callback(
            ExtractorLink(
                source = name,
                name = "Direct",
                url = videoUrl,
                referer = iframeUrl,
                quality = 720,
                type = com.lagradost.cloudstream3.utils.ExtractorLinkType.VIDEO
            )
        )

        return true

    }

}

