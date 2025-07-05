package binged

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import java.net.URLEncoder

class BingedProvider : MainAPI() {
    override var mainUrl = "https://www.binged.com"
    override var name = "Binged"
    override val supportedTypes = setOf(TvType.Movie)
    override var lang = "en"
    override val hasMainPage = true

    private suspend fun getData(titled: String, i: Int, fltr: String = ""): List<MovieSearchResponse> {
        val j = if (i == 1) 0 else 21 + (i - 2) * 20
        val response = app.post(
            "$mainUrl/wp-admin/admin-ajax.php",
            data = mapOf(
                "filters[recommend]" to "false",
                "filters[date-from]" to "",
                "filters[date-to]" to "",
                "filters[mode]" to titled,
                "action" to "mi_events_load_data",
                "mode" to titled,
                "start" to "$j",
                "length" to "20",
                "customcatalog" to "0"
            ),
            headers = mapOf(
                "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                "Accept" to "*/*",
                "X-Requested-With" to "XMLHttpRequest",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
                "Referer" to mainUrl
            )
        ).text

        val json = tryParseJson<Map<String, Any>>(response)
        var dataList = json?.get("data") as? List<Map<String, Any>>

        if (fltr.isNotEmpty()) {
            dataList = dataList?.filter { entry ->
                val platforms = entry["platform"] as? List<String>
                platforms?.any { it.contains(fltr, ignoreCase = true) } == true
            }
        }

        return dataList?.map { entry ->
            newMovieSearchResponse(
                name = entry["title"].toString(),
                url = entry["link"].toString(),
                type = TvType.Movie
            ) {
                this.posterUrl = entry["big-image"].toString()
            }
        } ?: emptyList()
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val stsoon = getData("streaming-soon", page)
        val stnow = getData("streaming-now", page)

        return newHomePageResponse(
            listOf(
                HomePageList("Streaming Soon", stsoon, false),
                HomePageList("Streaming Now", stnow, false),
                
            ), true
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.post(
            "$mainUrl/wp-admin/admin-ajax.php",
            data = mapOf(
                "action" to "mi_events_load_data",
                "test-search" to "1",
                "start" to "0",
                "length" to "20",
                "search[value]" to query,
                "customcatalog" to "0",
                "mode" to "all",
                "filters[search]" to query
            ),
            headers = mapOf(
                "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                "Accept" to "*/*",
                "X-Requested-With" to "XMLHttpRequest",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
                "Referer" to mainUrl
            )
        ).text

        val json = tryParseJson<Map<String, Any>>(response)
        val dataList = json?.get("data") as? List<Map<String, Any>>

        return dataList?.map { entry ->
            newMovieSearchResponse(
                name = entry["title"].toString(),
                url = entry["link"].toString(),
                type = TvType.Movie
            ) {
                this.posterUrl = entry["big-image"].toString()
            }
        } ?: emptyList()
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url, cacheTime = 60).document
        val title = doc.selectFirst("h1")?.text().orEmpty()
        val dt = doc.select("div.single-mevents-meta").text()
        val dtsplit = dt.split("|")
        val imageUrl = doc.selectFirst("meta[property=og:image]")?.attr("content").orEmpty()
        val trailer = doc.select("div.bng-section__content").getOrNull(1)
            ?.selectFirst("a")?.attr("href").orEmpty()
        val plot = doc.selectFirst("p")?.text().orEmpty()
        val year = dtsplit.getOrNull(0)?.trim()?.toIntOrNull()

        val tags = listOfNotNull(
            doc.selectFirst("span.single-mevents-platforms-row-date")?.text(),
            doc.selectFirst("span.rating-span")?.text(),
            doc.selectFirst("img.single-mevents-platforms-row-image")?.attr("alt"),
            doc.selectFirst("span.audiostring")?.text(),
            dtsplit.getOrNull(1),
            dtsplit.getOrNull(2),
            dtsplit.getOrNull(3)
        )

        return newMovieLoadResponse(title, url, TvType.Movie, null) {
            this.posterUrl = imageUrl
            this.year = year
            this.plot = plot
            this.tags = tags
            addTrailer(trailer)
        }
    }


    companion object {
        fun String.encodeUri() = URLEncoder.encode(this, "utf8")
    }
}
