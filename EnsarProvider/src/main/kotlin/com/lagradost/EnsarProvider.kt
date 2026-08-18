
package com.lagradost



import android.util.Log
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

    val ensarHtml = document.html()
    val ensarLower = ensarHtml.lowercase()

    for (keyword in listOf("vidara", "iframe", "embed", "filecode", "data-src", "player")) {
        println("ENSAR MAIN HAS $keyword = ${ensarLower.contains(keyword)}")
    }

    for (keyword in listOf("vidara", "iframe", "embed", "filecode", "data-src", "player")) {
        var pos = 0
        var count = 0

        while (count < 5) {
            val found = ensarLower.indexOf(keyword, pos)
            if (found == -1) break

            val from = maxOf(0, found - 300)
            val to = minOf(ensarHtml.length, found + 1000)

            println("ENSAR MAIN [$keyword] ${ensarHtml.substring(from, to)}")

            pos = found + keyword.length
            count++
        }
    }

    document
        .select("a[href*=\"/porn/\"]")
        .take(3)
        .forEachIndexed { i, element ->
            println("========== ENSAR ELEMENT $i ==========")
            println(element.outerHtml().take(5000))
            println("======================================")
        }



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
    println("ENSAR LOADLINKS ENTERED")
    Log.e("ENSAR_LINKS", "🔥 LOADLINKS ENTERED data=$data")

        Log.i("ENSAR_LINKS", "loadLinks BASLADI")
        Log.i("ENSAR_LINKS", "data=$data")

        val httpResponse = app.get(
            data,
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Linux; Android 13; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36",
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                "Accept-Language" to "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7",
                "Referer" to "https://www.porndish.com/",
                "Sec-Fetch-Dest" to "document",
                "Sec-Fetch-Mode" to "navigate",
                "Sec-Fetch-Site" to "same-origin",
                "Upgrade-Insecure-Requests" to "1"
            )
        )

        Log.i("ENSAR_RESPONSE", "STATUS=${httpResponse.code}")
        Log.i("ENSAR_RESPONSE", "URL=${httpResponse.url}")
        Log.i("ENSAR_RESPONSE", "CONTENT_TYPE=${httpResponse.headers["Content-Type"]}")
        Log.i("ENSAR_RESPONSE", "LOCATION=${httpResponse.headers["Location"]}")
        Log.i("ENSAR_RESPONSE", "SERVER=${httpResponse.headers["Server"]}")
        Log.i("ENSAR_RESPONSE", "SET_COOKIE=${httpResponse.headers["Set-Cookie"]}")

        val page = httpResponse.document

        Log.i("ENSAR_LINKS", "HTML IFRAME COUNT=${page.select("iframe").size}")

// === ENSAR HTML INSPECTION ===
val debugHtml = page.html()
val lowerHtml = debugHtml.lowercase()

Log.i("ENSAR_HTML", "RAW HTML SIZE=${debugHtml.length}")
Log.i("ENSAR_HTML", "HAS IFRAME=${lowerHtml.contains("<iframe")}")
Log.i("ENSAR_HTML", "HAS VIDEO=${lowerHtml.contains("<video")}")
Log.i("ENSAR_HTML", "HAS SOURCE=${lowerHtml.contains("<source")}")
Log.i("ENSAR_HTML", "HAS EMBED=${lowerHtml.contains("<embed")}")
Log.i("ENSAR_HTML", "HAS M3U8=${lowerHtml.contains(".m3u8")}")
Log.i("ENSAR_HTML", "HAS MP4=${lowerHtml.contains(".mp4")}")
Log.i("ENSAR_HTML", "HAS PLAYER=${lowerHtml.contains("player")}")

for (keyword in listOf("iframe", "video", "source", "embed", "m3u8", "mp4", "player")) {
    var pos = 0
    var count = 0

    Log.i("ENSAR_HTML", "=== SEARCH $keyword ===")

    while (count < 10) {
        val found = lowerHtml.indexOf(keyword, pos)
        if (found == -1) break

        val from = maxOf(0, found - 250)
        val to = minOf(debugHtml.length, found + 750)

        Log.i("ENSAR_HTML", debugHtml.substring(from, to))

        pos = found + keyword.length
        count++
    }

    Log.i("ENSAR_HTML", "=== END $keyword COUNT=$count ===")
}
// === END ENSAR HTML INSPECTION ===

        Log.i("ENSAR_LINKS", "HTML VIDEO COUNT=${page.select("video").size}")
        Log.i("ENSAR_LINKS", "HTML EMBED COUNT=${page.select("embed").size}")
        Log.i("ENSAR_LINKS", "HTML SCRIPT COUNT=${page.select("script").size}")

        page.select("script").forEachIndexed { i, script ->
            val text = script.html()

            Log.i("ENSAR_SCRIPT", "SCRIPT[$i] HTML LENGTH=${text.length}")
            Log.i("ENSAR_SCRIPT", "SCRIPT[$i] HTML=${text.take(10000)}")
        }

        page.select("iframe, video, embed").forEachIndexed { index, element ->
            Log.i(
                "ENSAR_LINKS",
                "ELEMENT[$index] tag=${element.tagName()} src=${element.attr("src")} data-src=${element.attr("data-src")} data-src-no-ap=${element.attr("data-src-no-ap")} outer=${element.outerHtml().take(1000)}"
            )
        }

        val iframeUrl = page.select("iframe[src], iframe[data-src-no-ap]")
            .firstOrNull()
            ?.let {
                it.attr("src").takeIf { src -> src.isNotBlank() }
                    ?: it.attr("data-src-no-ap").takeIf { src -> src.isNotBlank() }
            }
            ?: run {
                Log.i("ENSAR_LINKS", "iframe BULUNAMADI")
                return false
            }

        Log.i("ENSAR_LINKS", "iframe=$iframeUrl")

        val filecode = iframeUrl
            .substringAfterLast("/")
            .substringBefore("?")
            .takeIf { it.isNotBlank() }
            ?: run {
                Log.i("ENSAR_LINKS", "filecode BULUNAMADI")
                return false
            }

        Log.i("ENSAR_LINKS", "filecode=$filecode")

        val response = app.post(
            "https://vidara.to/api/stream",
            headers = mapOf(
                "Accept" to "application/json",
                "Content-Type" to "application/json",
                "Origin" to "https://vidara.to",
                "Referer" to "https://vidara.to/e/$filecode",
                "User-Agent" to "Mozilla/5.0"
            ),
            json = mapOf(
                "filecode" to filecode,
                "device" to "web"
            )
        ).parsedSafe<VidaraStreamResponse>()
            ?: run {
                Log.i("ENSAR_LINKS", "VIDARA RESPONSE PARSE EDILEMEDI")
                return false
            }

        val videoUrl = response.streaming_url
            .takeIf { it.startsWith("http") }
            ?: run {
                Log.i("ENSAR_LINKS", "STREAMING_URL YOK")
                return false
            }

        Log.i("ENSAR_LINKS", "videoUrl=$videoUrl")

        val link = newExtractorLink(
            source = "Vidara",
            name = "Vidara",
            url = videoUrl,
            type = ExtractorLinkType.M3U8
        ) {
            referer = "https://vidara.to/e/$filecode"
        }

        callback(link)

        Log.i("ENSAR_LINKS", "CALLBACK GONDERILDI")

        return true
    }

    data class VidaraStreamResponse(

        val streaming_url: String = "",

        val filecode: String = "",

        val title: String? = null

    )

}

