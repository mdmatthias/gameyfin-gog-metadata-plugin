package io.github.mdmatthias.gameyfin.plugins.gog

import io.github.resilience4j.bulkhead.Bulkhead
import io.github.resilience4j.bulkhead.BulkheadConfig
import io.github.resilience4j.decorators.Decorators
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.ratelimiter.RateLimiterConfig
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import me.xdrop.fuzzywuzzy.FuzzySearch
import org.gameyfin.pluginapi.core.wrapper.GameyfinPlugin
import org.gameyfin.pluginapi.gamemetadata.GameMetadata
import org.gameyfin.pluginapi.gamemetadata.GameMetadataProvider
import org.gameyfin.pluginapi.gamemetadata.Platform
import org.gameyfin.pluginapi.gamemetadata.Theme
import org.gameyfin.pluginapi.gamemetadata.GameFeature
import org.gameyfin.pluginapi.gamemetadata.Genre
import io.github.mdmatthias.gameyfin.plugins.gog.dto.*
import io.github.mdmatthias.gameyfin.plugins.gog.mapper.Mapper
import org.pf4j.Extension
import org.pf4j.PluginWrapper
import org.slf4j.LoggerFactory
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class GogPlugin(wrapper: PluginWrapper) : GameyfinPlugin(wrapper) {

    companion object {
        val jsonConfig = Json {
            isLenient = true
            ignoreUnknownKeys = true
        }
    }

    @Suppress("Unused")
    @Extension(ordinal = 3)
    class GogMetadataProvider : GameMetadataProvider {
        private val log = LoggerFactory.getLogger(javaClass)

        private val client = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(jsonConfig)
            }
        }

        companion object {
            private val catalogCache = object : java.util.LinkedHashMap<String, GogSearchResultItem>(100, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, GogSearchResultItem>): Boolean {
                    return size > 100
                }
            }
            
            private val gogRateLimiter: RateLimiter = RateLimiter.of(
                "gog-api",
                RateLimiterConfig.custom()
                    .limitForPeriod(4)
                    .limitRefreshPeriod(Duration.ofSeconds(1))
                    .timeoutDuration(Duration.ofMinutes(10))
                    .build()
            )
            private val bulkhead: Bulkhead = Bulkhead.of(
                "gog-api",
                BulkheadConfig.custom()
                    .maxConcurrentCalls(8)
                    .maxWaitDuration(Duration.ofMinutes(10))
                    .build()
            )
            private val retry: Retry = Retry.of(
                "gog-api",
                RetryConfig.custom<Any>()
                    .maxAttempts(3)
                    .waitDuration(Duration.ofSeconds(2))
                    .retryExceptions(Exception::class.java)
                    .build()
            )
        }

        override val supportedPlatforms: Set<Platform> =
            setOf(Platform.PC_MICROSOFT_WINDOWS, Platform.LINUX, Platform.MAC)

        override fun fetchByTitle(
            gameTitle: String,
            platformFilter: Set<Platform>,
            maxResults: Int
        ): List<GameMetadata> {
            val cleanedTitle = gameTitle.removeSuffix("_base").removeSuffix("_game")

            val searchResultItems = try {
                executeRequest { searchStore(cleanedTitle) }
            } catch (e: Exception) {
                log.error("Failed to search GOG store: ${e.message}")
                return emptyList()
            }

            if (searchResultItems.isEmpty()) return emptyList()

            val uniqueItems = searchResultItems.distinctBy { it.id }
            synchronized(catalogCache) {
                uniqueItems.forEach { catalogCache[it.id] = it }
            }

            val fuzzyResults = FuzzySearch.extractSorted(cleanedTitle, uniqueItems.map { it.title })
            val sortedItems = fuzzyResults
                .filter { it.score >= 60 }
                .map { uniqueItems[it.index] }
                .take(maxResults)

            return sortedItems.mapNotNull {
                val itemPlatforms = toGameyfinPlatforms(it.operatingSystems)
                
                if (platformFilter.isNotEmpty() && itemPlatforms.intersect(platformFilter).isEmpty()) {
                    return@mapNotNull null
                }

                // Call V2 API for each search result to get the description
                val description = fetchDescriptionFromApi(it.id)
                
                toGameMetadata(it, description)
            }
        }

        override fun fetchById(id: String): GameMetadata? {
            var catalogItem = synchronized(catalogCache) { catalogCache[id] }
            var description: String? = null

            try {
                // Always call V2 for details because it has the description
                val v2Product = executeRequest { 
                    val response = client.get("https://api.gog.com/v2/games/$id")
                    if (response.status == HttpStatusCode.OK) {
                        response.body<GogProductDetails>()
                    } else null
                }

                if (v2Product != null) {
                    description = v2Product.descriptionText
                    
                    if (catalogItem == null) {
                        val v2Title = v2Product.embedded?.product?.title ?: v2Product.title
                        if (v2Title != null) {
                            val results = executeRequest { searchStore(v2Title) }
                            
                            catalogItem = results.find { it.id.toString() == id.toString() }
                            
                            if (catalogItem == null) {
                                val fuzzy = FuzzySearch.extractOne(v2Title, results.map { it.title })
                                if (fuzzy != null && fuzzy.score >= 90) {
                                    catalogItem = results[fuzzy.index]
                                }
                            }
                            
                            if (catalogItem != null) {
                                synchronized(catalogCache) { catalogCache[id] = catalogItem!! }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                log.warn("Failed to fetch details for GOG app $id: ${e.message}")
            }

            return if (catalogItem != null) {
                toGameMetadata(catalogItem, description, overrideId = id)
            } else null
        }

        private fun fetchDescriptionFromApi(id: String): String? {
            return try {
                executeRequest {
                    val response = client.get("https://api.gog.com/v2/games/$id")
                    if (response.status == HttpStatusCode.OK) {
                        response.body<GogProductDetails>().descriptionText
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                log.warn("Failed to fetch description for $id: ${e.message}")
                null
            }
        }

        private fun <T> executeRequest(block: suspend () -> T): T {
            val supplier = { runBlocking { block() } }
            val decorated = Decorators.ofSupplier(supplier)
                .withBulkhead(bulkhead)
                .withRateLimiter(gogRateLimiter)
                .withRetry(retry)
                .decorate()
            return decorated.get()
        }

        private suspend fun searchStore(title: String): List<GogSearchResultItem> {
            val cleanedTitle = title.replace(":", " ")
                .replace("-", " ")
                .replace(Regex("[^A-Za-z0-9' ]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()

            val response = client.get("https://catalog.gog.com/v1/catalog") {
                parameter("limit", "20")
                parameter("order", "desc:score")
                parameter("productType", "in:game,pack")
                parameter("page", "1")
                parameter("query", cleanedTitle)
            }

            if (response.status != HttpStatusCode.OK) {
                return emptyList()
            }

            val searchResponse: GogSearchResponse = response.body()
            return searchResponse.products
        }

        private fun toGameMetadata(item: GogSearchResultItem, description: String? = null, overrideId: String? = null): GameMetadata {
            val coverUri = item.coverVertical?.let { fixUrl(it) }
            val headerUri = item.galaxyBackgroundImage?.let { fixUrl(it) }

            val parsedReleaseDate = try {
                 item.releaseDate?.let {
                    if (it.matches(Regex("^\\d{4}\\\$"))) {
                        LocalDate.of(it.toInt(), 1, 1).atStartOfDay(ZoneId.of("UTC")).toInstant()
                    } else if (it.matches(Regex("^\\d{10}\\\$"))) {
                        Instant.ofEpochSecond(it.toLong())
                    } else {
                         LocalDate.parse(it, DateTimeFormatter.ofPattern("yyyy.MM.dd"))
                            .atStartOfDay(ZoneId.of("UTC"))
                            .toInstant()
                    }
                 }
            } catch (e: Exception) {
                null
            }

            val allGogLabels = (item.genres?.map { it.name } ?: emptyList()) + 
                               (item.tags?.map { it.name } ?: emptyList()) +
                               (item.features?.map { it.name } ?: emptyList())
            val genres = allGogLabels.map { Mapper.genre(it) }.filter { it != Genre.UNKNOWN }.toSet()
            val themes = allGogLabels.map { Mapper.theme(it) }.filter { it != Theme.UNKNOWN }.toSet()
            val features = allGogLabels.mapNotNull { Mapper.feature(it) }.toSet()

            val screenshotUris = item.screenshots?.mapNotNull {
                fixUrl(it.replace("{formatter}", "1600"))
            }?.toSet()

            return GameMetadata(
                originalId = overrideId ?: item.id,
                title = item.title,
                platforms = toGameyfinPlatforms(item.operatingSystems),
                description = description,
                coverUrls = coverUri?.let { setOf(it) },
                headerUrls = headerUri?.let { setOf(it) },
                release = parsedReleaseDate,
                userRating = item.reviewsRating?.let { it * 2 },
                developedBy = item.developers?.toSet(),
                publishedBy = item.publishers?.toSet(),
                genres = genres,
                themes = themes,
                features = features,
                keywords = null,
                screenshotUrls = screenshotUris,
                videoUrls = null
            )
        }

        private fun fixUrl(url: String): URI? {
            return try {
                if (url.startsWith("//")) URI("https:$url") else URI(url)
            } catch (e: Exception) {
                null
            }
        }

        private fun toGameyfinPlatforms(os: List<String>?): Set<Platform> {
            return os?.mapNotNull {
                when (it.lowercase()) {
                    "windows" -> Platform.PC_MICROSOFT_WINDOWS
                    "linux" -> Platform.LINUX
                    "mac", "osx" -> Platform.MAC
                    else -> null
                }
            }?.toSet() ?: emptySet()
        }
    }
}