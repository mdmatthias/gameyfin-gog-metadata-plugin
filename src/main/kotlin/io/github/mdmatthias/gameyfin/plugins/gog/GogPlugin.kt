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

            private val metadataCache = object : java.util.LinkedHashMap<String, GameMetadata>(100, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, GameMetadata>): Boolean {
                    return size > 100
                }
            }
            
            // Unified rate limiter: 1 request per second
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
                .replace("_", " ").trim()

            // 1. Search both APIs
            val catalogResults = try {
                executeRequest { searchStore(cleanedTitle) }
            } catch (e: Exception) {
                log.error("Failed to search GOG catalog: ${e.message}")
                emptyList()
            }

            val gamesDbResults = try {
                executeRequest { searchGamesDb(cleanedTitle) }
            } catch (e: Exception) {
                log.error("Failed to search GOG GamesDb: ${e.message}")
                emptyList()
            }

            val allMetadataWithPriority = mutableListOf<Pair<GameMetadata, Int>>()

            catalogResults.forEach { item ->
                synchronized(catalogCache) { catalogCache[item.id] = item }
                val itemPlatforms = toGameyfinPlatforms(item.operatingSystems)
                if (platformFilter.isEmpty() || itemPlatforms.intersect(platformFilter).isNotEmpty()) {
                    val metadata = toGameMetadata(item, null)
                    allMetadataWithPriority.add(metadata to 1) // Source 1 = Catalog
                }
            }

            gamesDbResults.forEach { item ->
                val displayTitle = if (item.slug != null && gamesDbResults.count { it.title == item.title } > 1) {
                    "${item.title} (${item.slug})"
                } else {
                    item.title
                }
                val metadata = toGameMetadataFromGamesDb(item).copy(title = displayTitle)
                allMetadataWithPriority.add(metadata to 0) // Source 0 = GamesDb
            }

            if (allMetadataWithPriority.isEmpty()) return emptyList()

            // Filter and sort
            val sortedResults = allMetadataWithPriority
                .map { it to FuzzySearch.weightedRatio(cleanedTitle.lowercase(), it.first.title.lowercase()) }
                .filter { it.second >= 60 }
                .sortedWith(
                    compareByDescending<Pair<Pair<GameMetadata, Int>, Int>> { it.first.first.title.trim().equals(cleanedTitle.trim(), ignoreCase = true) }
                        .thenByDescending { it.second } // Fuzzy score
                        .thenByDescending { if (it.first.first.description.isNullOrBlank()) 0 else 1 }
                        .thenByDescending { it.first.second } // Source priority
                )

            // Deduplicate by normalized title + year
            val seen = mutableMapOf<String, Pair<GameMetadata, Int>>()
            
            for (result in sortedResults) {
                val metadata = result.first.first
                val source = result.first.second
                val year = metadata.release?.let { java.time.OffsetDateTime.ofInstant(it, java.time.ZoneOffset.UTC).year }
                val key = "${metadata.title.lowercase().replace(Regex("[^a-z0-9]"), "")}_$year"
                
                val existing = seen[key]
                if (existing == null) {
                    seen[key] = metadata to source
                } else {
                    // If we have a duplicate, merge it if the new one has better images
                    val existingMetadata = existing.first
                    if (existingMetadata.coverUrls.isNullOrEmpty() && !metadata.coverUrls.isNullOrEmpty()) {
                        seen[key] = existingMetadata.copy(coverUrls = metadata.coverUrls) to existing.second
                    }
                    if (existingMetadata.headerUrls.isNullOrEmpty() && !metadata.headerUrls.isNullOrEmpty()) {
                        val current = seen[key]!!
                        seen[key] = current.first.copy(headerUrls = metadata.headerUrls) to current.second
                    }
                }
            }

            return seen.values
                .take(maxResults)
                .map { (metadata, source) ->
                    // Fetch description if missing (especially for Catalog results)
                    val finalMetadata = if (metadata.description.isNullOrBlank()) {
                        val description = fetchDescriptionFromApi(metadata.originalId)
                        if (description != null) metadata.copy(description = description) else metadata
                    } else {
                        metadata
                    }
                    
                    synchronized(metadataCache) { metadataCache[finalMetadata.originalId] = finalMetadata }
                    finalMetadata 
                }
        }

        override fun fetchById(id: String): GameMetadata? {
            synchronized(metadataCache) {
                metadataCache[id]?.let { return it }
            }

            var catalogItem = synchronized(catalogCache) { catalogCache[id] }
            var description: String? = null

            try {
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
                val metadata = toGameMetadata(catalogItem, description, overrideId = id)
                synchronized(metadataCache) { metadataCache[id] = metadata }
                metadata
            } else null
        }

        private fun fetchDescriptionFromApi(id: String): String? {
            return try {
                executeRequest {
                    val response = client.get("https://api.gog.com/v2/games/$id")
                    if (response.status == HttpStatusCode.OK) {
                        response.body<GogProductDetails>().descriptionText
                    } else null
                }
            } catch (e: Exception) {
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
                parameter("limit", "50")
                parameter("order", "desc:score")
                parameter("productType", "in:game,pack")
                parameter("page", "1")
                parameter("query", cleanedTitle)
            }

            if (response.status != HttpStatusCode.OK) return emptyList()
            val searchResponse: GogSearchResponse = response.body()
            return searchResponse.products
        }

        private suspend fun searchGamesDb(title: String): List<GogGamesDbItem> {
            val response = client.get("https://gamesdb.gog.com/wishlist/wishlisted_games") {
                parameter("title", title)
                parameter("sort", "relevance")
                parameter("limit", "50")
                parameter("show_only_unreleased", "0")
            }

            if (response.status != HttpStatusCode.OK) return emptyList()
            val gamesDbResponse: GogGamesDbResponse = response.body()
            return gamesDbResponse.items
        }

        private fun toGameMetadata(item: GogSearchResultItem, description: String? = null, overrideId: String? = null): GameMetadata {
            val coverUri = item.coverVertical?.let { fixUrl(it) }
            val headerUri = item.galaxyBackgroundImage?.let { fixUrl(it) }

            val parsedReleaseDate = try {
                 item.releaseDate?.let {
                    if (it.matches(Regex("^\\d{4}$"))) {
                        LocalDate.of(it.toInt(), 1, 1).atStartOfDay(ZoneId.of("UTC")).toInstant()
                    } else if (it.matches(Regex("^\\d{10}$"))) {
                        Instant.ofEpochSecond(it.toLong())
                    } else {
                         LocalDate.parse(it, DateTimeFormatter.ofPattern("yyyy.MM.dd"))
                            .atStartOfDay(ZoneId.of("UTC"))
                            .toInstant()
                    }
                 }
            } catch (e: Exception) { null }

            val allGogLabels = (item.genres?.map { it.name } ?: emptyList()) + 
                               (item.tags?.map { it.name } ?: emptyList()) + 
                               (item.features?.map { it.name } ?: emptyList())
            val genres = allGogLabels.map { Mapper.genre(it) }.filter { it != Genre.UNKNOWN }.toSet()
            val themes = allGogLabels.map { Mapper.theme(it) }.filter { it != Theme.UNKNOWN }.toSet()
            val features = allGogLabels.mapNotNull { Mapper.feature(it) }.toSet()

            val screenshotUris = item.screenshots?.mapNotNull { url ->
                fixUrl(url.replace("{formatter}", "1600"))
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

        private fun toGameMetadataFromGamesDb(item: GogGamesDbItem): GameMetadata {
            val coverUrl = item.verticalCover?.urlFormat ?: item.cover?.urlFormat
            val coverUri = coverUrl?.let { fixGamesDbUrl(it, "_glx_vertical_cover") }

            val headerUrl = item.background?.urlFormat ?: item.horizontal_artwork?.urlFormat
            val headerUri = headerUrl?.let { fixGamesDbUrl(it, "_1600") }

            val parsedReleaseDate = try {
                item.firstReleaseDate?.let { Instant.parse(it) }
            } catch (e: Exception) { null }

            val allGogLabels = (item.genres?.map { it.name } ?: emptyList()) + 
                               (item.themes?.map { it.name } ?: emptyList()) + 
                               (item.gameModes?.map { it.name } ?: emptyList())
            val genres = allGogLabels.map { Mapper.genre(it) }.filter { it != Genre.UNKNOWN }.toSet()
            val themes = allGogLabels.map { Mapper.theme(it) }.filter { it != Theme.UNKNOWN }.toSet()
            val features = allGogLabels.mapNotNull { Mapper.feature(it) }.toSet()

            val screenshotUris = item.screenshots?.mapNotNull { s -> fixGamesDbUrl(s.urlFormat, "_1600") }?.toSet()
            val description = item.summary?.get("en-US") ?: item.summary?.get("*")

            return GameMetadata(
                originalId = item.id,
                title = item.title,
                platforms = setOf(Platform.PC_MICROSOFT_WINDOWS),
                description = description,
                coverUrls = coverUri?.let { setOf(it) },
                headerUrls = headerUri?.let { setOf(it) },
                release = parsedReleaseDate,
                userRating = null,
                developedBy = item.developers?.map { it.name }?.toSet(),
                publishedBy = item.publishers?.map { it.name }?.toSet(),
                genres = genres,
                themes = themes,
                features = features,
                keywords = null,
                screenshotUrls = screenshotUris,
                videoUrls = null
            )
        }

        private fun fixUrl(url: String): URI? {
            return try { if (url.startsWith("//")) URI("https:$url") else URI(url) } catch (e: Exception) { null }
        }

        private fun fixGamesDbUrl(format: String, formatter: String): URI? {
            val url = format.replace("{formatter}", formatter).replace("{ext}", "jpg")
            return fixUrl(url)
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
