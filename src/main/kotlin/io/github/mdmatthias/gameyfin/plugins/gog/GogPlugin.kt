package io.github.mdmatthias.gameyfin.plugins.gog

import io.github.resilience4j.bulkhead.Bulkhead
import io.github.resilience4j.bulkhead.BulkheadConfig
import io.github.resilience4j.decorators.Decorators
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.ratelimiter.RateLimiterConfig
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
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
import io.github.mdmatthias.gameyfin.plugins.gog.dto.GogProductDetails
import io.github.mdmatthias.gameyfin.plugins.gog.dto.GogSearchResponse
import io.github.mdmatthias.gameyfin.plugins.gog.dto.GogSearchResultItem
import io.github.mdmatthias.gameyfin.plugins.gog.dto.GogSystemCompatibility
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
            // api.gog.com has a rate limit of 200/hour -> 20/6min
            private val rateLimiter: RateLimiter = RateLimiter.of(
                "gog-api",
                RateLimiterConfig.custom()
                    .limitForPeriod(20)
                    .limitRefreshPeriod(Duration.ofMinutes(6))
                    .timeoutDuration(Duration.ofSeconds(5))
                    .build()
            )
            // catalog.gog.com does not seem to have a rate limit, but lets limit it to some sane values
            private val searchRateLimiter: RateLimiter = RateLimiter.of(
                "gog-search-api",
                RateLimiterConfig.custom()
                    .limitForPeriod(4)
                    .limitRefreshPeriod(Duration.ofSeconds(1))
                    .timeoutDuration(Duration.ofSeconds(2))
                    .build()
            )
            private val bulkhead: Bulkhead = Bulkhead.of(
                "gog-api",
                BulkheadConfig.custom()
                    .maxConcurrentCalls(8)
                    .maxWaitDuration(Duration.ofMinutes(10))
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
            val searchResultItems = mutableListOf<GogSearchResultItem>()

            try {
                searchResultItems.addAll(gogSearchCall { searchStore(gameTitle) })
            } catch (e: Exception) {
                log.error("Failed to search GOG store: ${e.message}")
            }

            val normalizedTitle = gameTitle.replace(Regex("[^A-Za-z0-9 ]"), " ")
            if (normalizedTitle.trim().endsWith(" game", ignoreCase = true)) {
                val titleWithoutSuffix = normalizedTitle.substring(0, normalizedTitle.lastIndexOf(" game", ignoreCase = true)).trim()
                if (titleWithoutSuffix.isNotEmpty()) {
                    try {
                        searchResultItems.addAll(gogSearchCall { searchStore(titleWithoutSuffix) })
                    } catch (e: Exception) {
                        log.warn("Failed to search GOG store for alternative title: ${e.message}")
                    }
                }
            }

            if (searchResultItems.isEmpty()) return emptyList()

            val uniqueItems = searchResultItems.distinctBy { it.id }

            synchronized(catalogCache) {
                uniqueItems.forEach { catalogCache[it.id] = it }
            }

            // Sort search results by fuzzy match with the requested title and filter by a minimum score (60)
            // This prevents that you get garbage results when a game is not found in the gog catalog
            val fuzzyResults = FuzzySearch.extractSorted(gameTitle, uniqueItems.map { it.title })
            val sortedItems = fuzzyResults
                .filter { it.score >= 60 }
                .map { uniqueItems[it.index] }

            return sortedItems.asSequence()
                .filter { item ->
                    // Basic platform filtering based on what's available in the search result
                    val itemPlatforms = item.operatingSystems?.mapNotNull { os ->
                        when (os.lowercase()) {
                            "windows" -> Platform.PC_MICROSOFT_WINDOWS
                            "linux" -> Platform.LINUX
                            "mac", "osx" -> Platform.MAC
                            else -> null
                        }
                    }?.toSet() ?: emptySet()

                    if (platformFilter.isEmpty()) true else itemPlatforms.intersect(platformFilter).isNotEmpty()
                }
                .map { toGameMetadata(it) }
                .take(maxResults)
                .toList()
        }

        override fun fetchById(id: String): GameMetadata? {
            return try {
                gogApiCall { getGameDetails(id) }
            } catch (e: Exception) {
                log.warn("Failed to fetch details for GOG app $id: ${e.message}")
                null
            }
        }

        private fun toGameMetadata(item: GogSearchResultItem): GameMetadata {
            val coverUrl = item.coverVertical
            val coverUri = coverUrl?.let {
                if (it.startsWith("//")) URI("https:$it") else URI(it)
            }

            val headerUrl = item.galaxyBackgroundImage
            val headerUri = headerUrl?.let {
                if (it.startsWith("//")) URI("https:$it") else URI(it)
            }

            // GOG Search result dates are often just "YYYY", "YYYY-MM-dd", or sometimes Unix timestamp.
            // But based on DTO it's a String. Let's try basic parsing or ignore if complex.
            // item.releaseDate might be "2015" or "1369288800"
            val parsedReleaseDate = try {
                 item.releaseDate?.let {
                    if (it.matches(Regex("^\\d{4}$"))) {
                        LocalDate.of(it.toInt(), 1, 1).atStartOfDay(ZoneId.of("UTC")).toInstant()
                    } else if (it.matches(Regex("^\\d{10}$"))) {
                        Instant.ofEpochSecond(it.toLong())
                    } else {
                        // Try standard format
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

            val screenshotUris = item.screenshots?.mapNotNull { url ->
                try {
                    val formattedUrl = url.replace("{formatter}", "1600")
                    if (formattedUrl.startsWith("//")) URI("https:$formattedUrl") else URI(formattedUrl)
                } catch (e: Exception) {
                    null
                }
            }?.toSet()

            val itemPlatforms = item.operatingSystems?.mapNotNull { os ->
                when (os.lowercase()) {
                    "windows" -> Platform.PC_MICROSOFT_WINDOWS
                    "linux" -> Platform.LINUX
                    "mac", "osx" -> Platform.MAC
                    else -> null
                }
            }?.toSet() ?: emptySet()

            return GameMetadata(
                originalId = item.id,
                title = item.title,
                platforms = itemPlatforms,
                description = null, // Description is not available in search results
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

        // Helper to enforce rate limit + bulkhead around suspend HTTP operations
        private fun <T> gogApiCall(block: suspend () -> T): T {
            val supplier = { runBlocking { block() } }
            val decorated = Decorators.ofSupplier(supplier)
                .withBulkhead(bulkhead)
                .withRateLimiter(rateLimiter)
                .decorate()
            return decorated.get()
        }

        // Helper to enforce rate limit for search operations
        private fun <T> gogSearchCall(block: suspend () -> T): T {
            val supplier = { runBlocking { block() } }
            val decorated = Decorators.ofSupplier(supplier)
                .withRateLimiter(searchRateLimiter)
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
                log.warn("GOG search returned HTTP ${response.status}")
                return emptyList()
            }

            val searchResponse: GogSearchResponse = response.body()
            return searchResponse.products
        }

        private suspend fun getGameDetails(
            id: String,
            platformFilter: Set<Platform> = emptySet(),
            catalogItem: GogSearchResultItem? = null
        ): GameMetadata? {
            val response = client.get("https://api.gog.com/products/$id") {
                parameter("expand", "description")
            }

            if (response.status != HttpStatusCode.OK) return null

            val product: GogProductDetails = response.body()

            // Try to find catalog item in cache, or find by title search if missing
            val finalCatalogItem = catalogItem ?: synchronized(catalogCache) { catalogCache[id] } ?: try {
                val results = searchStore(product.title)
                val found = results.find { it.id == id }
                if (found != null) {
                    synchronized(catalogCache) { catalogCache[id] = found }
                }
                found
            } catch (e: Exception) {
                null
            }

            // Map platforms
            val gamePlatforms = if (product.systemCompatibility != null) {
                toGameyfinPlatforms(product.systemCompatibility)
            } else {
                emptySet()
            }

            val filteredPlatforms = if (platformFilter.isNotEmpty()) {
                gamePlatforms.intersect(platformFilter)
            } else {
                gamePlatforms
            }

            if (filteredPlatforms.isEmpty() && platformFilter.isNotEmpty()) return null

            val coverUrl = finalCatalogItem?.coverVertical ?: product.images?.logo2x ?: product.images?.logo
            val coverUri = coverUrl?.let {
                 if (it.startsWith("//")) URI("https:$it") else URI(it)
            }

            val headerUrl = finalCatalogItem?.galaxyBackgroundImage ?: product.images?.background
            val headerUri = headerUrl?.let {
                if (it.startsWith("//")) URI("https:$it") else URI(it)
            }

            val parsedReleaseDate = try {
                finalCatalogItem?.releaseDate?.let {
                    LocalDate.parse(it, DateTimeFormatter.ofPattern("yyyy.MM.dd"))
                        .atStartOfDay(ZoneId.of("UTC"))
                        .toInstant()
                } ?: product.releaseDate?.let {
                    // Handle ISO 8601 with offset without colon (e.g. +0300)
                    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")
                    formatter.parse(it, Instant::from)
                }
            } catch (e: Exception) {
                log.warn("Failed to parse release date for $id", e)
                null
            }

            val gogGenres = finalCatalogItem?.genres ?: product.genres
            val gogTags = finalCatalogItem?.tags ?: product.tags
            val gogFeatures = finalCatalogItem?.features ?: product.features
            val allGogLabels = (gogGenres?.map { it.name } ?: emptyList()) + 
                               (gogTags?.map { it.name } ?: emptyList()) +
                               (gogFeatures?.map { it.name } ?: emptyList())

            val genres = allGogLabels.map { Mapper.genre(it) }.filter { it != Genre.UNKNOWN }.toSet()
            val themes = allGogLabels.map { Mapper.theme(it) }.filter { it != Theme.UNKNOWN }.toSet()
            val features = allGogLabels.mapNotNull { Mapper.feature(it) }.toSet()

            val screenshotUris = finalCatalogItem?.screenshots?.mapNotNull { url ->
                try {
                    val formattedUrl = url.replace("{formatter}", "1600")
                    if (formattedUrl.startsWith("//")) URI("https:$formattedUrl") else URI(formattedUrl)
                } catch (e: Exception) {
                    null
                }
            }?.toSet()

            val metadata = GameMetadata(
                originalId = id,
                title = product.title,
                platforms = filteredPlatforms,
                description = product.description?.full ?: product.description?.lead,
                coverUrls = coverUri?.let { setOf(it) },
                headerUrls = headerUri?.let { setOf(it) },
                release = parsedReleaseDate,
                userRating = finalCatalogItem?.reviewsRating?.let { it * 2 },
                developedBy = (product.developers ?: finalCatalogItem?.developers)?.toSet(),
                publishedBy = (product.publishers ?: finalCatalogItem?.publishers)?.toSet(),
                genres = genres,
                themes = themes,
                features = features,
                keywords = null,
                screenshotUrls = screenshotUris,
                videoUrls = null
            )

            return metadata
        }

        private fun toGameyfinPlatforms(compatibility: GogSystemCompatibility): Set<Platform> {
            val platforms = mutableSetOf<Platform>()
            if (compatibility.windows) platforms.add(Platform.PC_MICROSOFT_WINDOWS)
            if (compatibility.linux) platforms.add(Platform.LINUX)
            if (compatibility.osx) platforms.add(Platform.MAC)
            return platforms
        }
    }
}