package io.github.mdmatthias.gameyfin.plugins.gog.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GogProductDetails(
    val id: Int,
    val title: String,
    val slug: String? = null,
    val images: GogImages? = null,
    @SerialName("content_system_compatibility") val systemCompatibility: GogSystemCompatibility? = null,
    val genres: List<GogGenre>? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    val description: GogDescription? = null,
    val developers: List<String>? = null,
    val publishers: List<String>? = null
)

@Serializable
data class GogImages(
    val background: String? = null,
    val logo: String? = null,
    val logo2x: String? = null,
    // Sometimes it's an array of screenshots/gallery, but typical product api response structure varies.
    // We might need to handle screenshots differently if not present here or use another endpoint.
    // For now, let's assume we can get basic images.
)

@Serializable
data class GogSystemCompatibility(
    val windows: Boolean = false,
    val osx: Boolean = false,
    val linux: Boolean = false
)

@Serializable
data class GogGenre(
    val name: String
)

@Serializable
data class GogDescription(
    val lead: String? = null,
    val full: String? = null
)
