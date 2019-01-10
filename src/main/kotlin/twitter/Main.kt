package twitter

import com.opencsv.CSVWriter
import net.sourceforge.argparse4j.ArgumentParsers
import net.sourceforge.argparse4j.inf.ArgumentParserException
import net.sourceforge.argparse4j.inf.Namespace
import java.io.File
import java.io.FileWriter
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.system.exitProcess


fun main(args: Array<String>) {
    val parser = ArgumentParsers.newFor("Twitter Crowler Command Line Tool").build().defaultHelp(true)
        .description("Twitter Crowler Command Line Tool")
    parser.addArgument("-a", "--account").help("account name").required(true)
    parser.addArgument("-o", "--output").help("csv output folder").required(false)
    val namespace: Namespace
    try {
        namespace = parser.parseArgs(args)
        println(namespace)
    } catch (e: ArgumentParserException) {
        parser.handleError(e)
        exitProcess(1)
    }
    val topic = namespace.getString("account")
    val outputDirPath = namespace.getString("output")!! + "_" + getTimeStamp()

    val spider = TwitterSpider()
    val cal = Calendar.getInstance()
    val now = cal.time
    cal.add(Calendar.MONTH, -12)
    val from = cal.time
    if (!File(outputDirPath).exists())
        File(outputDirPath).mkdir()
    var fileWriter = File(outputDirPath, topic)

    fileWriter.bufferedWriter().use { writer ->
        spider.crawlTweets(topic, from, now) {
            writer.appendln("${it.id},${it.userName},${it.createdAt},${it.text}")
        }
    }


}

private fun getTimeStamp(): String {
    return DateTimeFormatter
        .ofPattern("yyyyMMdd_HHmm")
        .withZone(ZoneOffset.systemDefault())
        .format(Instant.now())
}

