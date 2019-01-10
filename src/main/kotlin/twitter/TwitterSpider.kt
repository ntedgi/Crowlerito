package twitter

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.opencsv.CSVWriter
import org.jsoup.Jsoup
import org.jsoup.select.Elements
import java.io.BufferedReader
import java.io.FileWriter
import java.io.InputStreamReader
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*


class TwitterSpider {


    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Ubuntu Chromium/68.0.3440.106 Chrome/68.0.3440.106 Safari/537.36"
        private val GSON = Gson()
    }

    var needToStop = false


    fun crawlTweets(from: String, since: Date, until: Date, action: (Tweet) -> Unit) {
        var tweetsScraed = 0

        val df = SimpleDateFormat("yyyy-MM-dd")

        val cal = Calendar.getInstance()
        cal.time = since
        var current = cal.time

        while (true) {
            cal.add(Calendar.DATE, 1)
            val next = cal.time

            val query = "from%3A$from%20since%3A${df.format(current)}%20until%3A${df.format(next)}"
            val startURL = "https://twitter.com/search?q=$query&src=typd&lang=en"

            println("fetching tweets in range: ${df.format(current)} -> ${df.format(next)}")

            if (next.after(until))
                break

            current = next

            var doc = Jsoup.connect(startURL).userAgent(USER_AGENT).get()
            val timeline = doc.select("#timeline")

            if (timeline != null && !timeline.isEmpty()) {

                val initData = doc.getElementById("init-data").attr("value")
                val jInitData = GSON.fromJson(initData, JsonObject::class.java)
                val endPoint = jInitData.get("searchEndpoint").asString

                val streamContainer = timeline.first().select("div.stream-container").first()

                var maxPosition = streamContainer.attr("data-min-position")

                var tweets = streamContainer.select("li.stream-item")

                System.out.println(String.format("%s - Found %d tweet(s) ..", getCurrentTime(), tweets.size))

                var writed = processTweets(tweets, action)

                tweetsScraed += writed

                System.out.println(String.format("%s - Writed %d tweet(s) ..", getCurrentTime(), writed));

                while (!maxPosition.isEmpty() && !this.needToStop) {
                    val nextPage = getNextPage(endPoint, maxPosition)

                    try {


                        val jsonObj = GSON.fromJson(nextPage, JsonObject::class.java)

                        if (!jsonObj.get("has_more_items").asBoolean)
                            break

                        maxPosition = jsonObj.get("min_position").asString

                        doc = Jsoup.parse(jsonObj.get("items_html").asString)

                        tweets = doc.select("li.stream-item")

                        if (tweets.isEmpty()) {
                            break
                        }

                        System.out.println(String.format("%s - Found %d tweet(s) ..", getCurrentTime(), tweets.size))

                        writed = processTweets(tweets, action)

                        tweetsScraed += writed

                        System.out.println(String.format("%s - Writed %d tweet(s) ..", getCurrentTime(), writed))
                    } catch (e: Exception) {
                        e.printStackTrace()
                        break
                    }
                }

            }

            Thread.sleep((1_300 * Math.random()).toLong())
        }

        System.out.println()
    }


    private fun getNextPage(searchEndPoint: String, maxPosition: String): String {
        val stringBuilder = StringBuilder()

        val autoLoadURL = "https://twitter.com" +
                searchEndPoint +
                "&include_available_features=1&include_entities=1" +
                "&max_position=" + maxPosition + "&reset_error_state=false"
        val url = URL(autoLoadURL)
        val conn = url.openConnection()
        conn.setRequestProperty("User-Agent", USER_AGENT)

        BufferedReader(InputStreamReader(conn.getInputStream())).useLines { lines ->
            lines.forEach { line ->
                stringBuilder.append(line)
            }
        }

        return stringBuilder.toString()
    }

    private fun processTweets(tweets: Elements, action: (Tweet) -> Unit): Int {

        var writed = 0

        for (tweet in tweets) {
            val tweetID = tweet.attr("data-item-id").toLong()

            val tweetTime = tweet.select("a.tweet-timestamp")
            var createdAt = ""
            if (!tweetTime.isEmpty()) {
                createdAt = tweetTime.first().attr("title")
            }

            val tweetsTexts = tweet.select("p.tweet-text")

            if (!tweetsTexts.isEmpty()) {
                val tweetText = tweetsTexts.first().text()

                val user = tweet.select("span.username")

                val userName = if (!user.isEmpty()) {
                    user.first().text()
                } else {
                    ""
                }

                action(Tweet(tweetID, userName, createdAt, tweetText))
                writed++

            }

        }
        return writed
    }

    private fun getCurrentTime(): String {
        val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm:ss")
        val date = Date()
        return dateFormat.format(date)
    }

    data class Tweet(val id: Long, val userName: String, val createdAt: String, val text: String) {
        override fun toString(): String {
            return "{id:$id , username:$userName , createdAt:$createdAt , text:$text}"
        }
    }
}
