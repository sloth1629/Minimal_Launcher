package com.example.minimalwidget.data.repository

import android.util.Xml
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

interface NewsRepository {
    suspend fun getNewsSummaries(limit: Int = 3, interests: String = "AI, IT"): List<String>
}

class MockNewsRepository : NewsRepository {
    private val sampleHeadlines = listOf(
        "GPT-5.5 이미지 처리속도 2배 증가",
        "미국 기준금리 동결",
        "초전도 연구팀 주요 실험결과 발표"
    )

    override suspend fun getNewsSummaries(limit: Int, interests: String): List<String> {
        return sampleHeadlines.take(limit)
    }
}

class GoogleRssNewsRepository : NewsRepository {
    override suspend fun getNewsSummaries(limit: Int, interests: String): List<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val query = interests
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .joinToString(" OR ")
                .ifBlank { "technology" }

            val url = "https://news.google.com/rss/search".toUri()
                .buildUpon()
                .appendQueryParameter("q", query)
                .appendQueryParameter("hl", "ko")
                .appendQueryParameter("gl", "KR")
                .appendQueryParameter("ceid", "KR:ko")
                .build()
                .toString()

            val headlines = fetchRssTitles(url, limit)
            if (headlines.isEmpty()) fallback(limit) else headlines
        } catch (_: Exception) {
            fallback(limit)
        }
    }

    private fun fetchRssTitles(url: String, limit: Int): List<String> {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 7000
            readTimeout = 7000
        }

        return try {
            if (connection.responseCode !in 200..299) {
                throw IOException("HTTP ${connection.responseCode}")
            }

            connection.inputStream.use { input ->
                val parser = Xml.newPullParser()
                parser.setInput(input, null)

                val titles = mutableListOf<String>()
                var eventType = parser.eventType
                var insideItem = false

                while (eventType != XmlPullParser.END_DOCUMENT && titles.size < limit) {
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            if (parser.name == "item") {
                                insideItem = true
                            } else if (insideItem && parser.name == "title") {
                                val title = parser.nextText().trim()
                                if (title.isNotBlank()) {
                                    titles += cleanTitle(title)
                                }
                            }
                        }

                        XmlPullParser.END_TAG -> {
                            if (parser.name == "item") {
                                insideItem = false
                            }
                        }
                    }

                    eventType = parser.next()
                }

                titles
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun cleanTitle(raw: String): String {
        return raw.substringBefore(" - ").trim()
    }

    private fun fallback(limit: Int): List<String> {
        return listOf(
            "실시간 뉴스를 불러오지 못했어요",
            "네트워크 상태를 확인해 주세요",
            "잠시 후 다시 시도해 주세요"
        ).take(limit)
    }
}

class DcSingularityRecommendNewsRepository : NewsRepository {
    private val fallbackRss = GoogleRssNewsRepository()

    override suspend fun getNewsSummaries(limit: Int, interests: String): List<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val recommendUrl = "https://gall.dcinside.com/mgallery/board/lists/?id=thesingularity&exception_mode=recommend"
            val normalUrl = "https://gall.dcinside.com/mgallery/board/lists/?id=thesingularity"
            val mobileUrl = "https://m.dcinside.com/board/thesingularity"

            val fromRecommend = parseTitles(requestHtmlUtf8(recommendUrl), limit)
            if (fromRecommend.isNotEmpty()) return@withContext fromRecommend

            val fromNormal = parseTitles(requestHtmlUtf8(normalUrl), limit)
            if (fromNormal.isNotEmpty()) return@withContext fromNormal

            val fromMobile = parseTitles(requestHtmlUtf8(mobileUrl), limit)
            if (fromMobile.isNotEmpty()) fromMobile else fallbackRss.getNewsSummaries(limit, interests)
        } catch (_: Exception) {
            fallbackRss.getNewsSummaries(limit, interests)
        }
    }

    private fun requestHtmlUtf8(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 7000
            readTimeout = 7000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
            setRequestProperty("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8")
        }

        return try {
            if (connection.responseCode !in 200..299) {
                throw IOException("HTTP ${connection.responseCode}")
            }
            InputStreamReader(connection.inputStream, Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseTitles(html: String, limit: Int): List<String> {
        val pattern = Regex(
            """<a\s+[^>]*href=[\"']/(?:mgallery/)?board/view/\?id=thesingularity[^\"']*[\"'][^>]*>(.*?)</a>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )

        val titles = mutableListOf<String>()
        for (match in pattern.findAll(html)) {
            if (titles.size >= limit) break
            val anchorInner = match.groupValues[1]

            if (anchorInner.contains("icon_notice")) continue

            val cleaned = decodeHtml(stripTags(anchorInner))
                .replace(Regex("\\s+"), " ")
                .trim()

            if (cleaned.length < 4) continue
            titles += cleaned
        }

        return titles.distinct().take(limit)
    }

    private fun stripTags(input: String): String = input.replace(Regex("<[^>]+>"), "")

    private fun decodeHtml(input: String): String {
        return input
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
    }
}
