import kotlinx.coroutines.runBlocking
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

val client = HttpClient(OkHttp)
val videoId = "dQw4w9WgXcQ"
val instances = listOf(
    "https://pipedapi.kavin.rocks",
    "https://vid.puffyan.us",
    "https://pipedapi.drgns.space",
    "https://api.piped.privacy.com.de",
    "https://invidious.privacyredirect.com",
    "https://invidious.projectsegfau.lt"
)

runBlocking {
    for (instance in instances) {
        try {
            println("Testing instance: $instance")
            val res = client.get("$instance/api/v1/videos/$videoId")
            println("Status: ${res.status.value}")
            if (res.status.value in 200..299) {
                val body = res.bodyAsText()
                val json = Json.parseToJsonElement(body) as? JsonObject
                val audioStreams = json?.get("audioStreams")?.jsonArray
                val url = audioStreams?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content
                println("Resolved URL: $url")
                if (!url.isNullOrBlank()) {
                    println("SUCCESS with $instance -> $url")
                    return@runBlocking
                }
            }
        } catch (e: Exception) {
            println("Failed $instance: ${e.message}")
        }
    }
}
