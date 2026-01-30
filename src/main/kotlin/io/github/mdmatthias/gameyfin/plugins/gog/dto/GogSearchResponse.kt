package io.github.mdmatthias.gameyfin.plugins.gog.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GogSearchResponse(
    val products: List<GogSearchResultItem> = emptyList()
)

@Serializable
data class GogSearchResultItem(
    val id: String,
    val title: String,
    val slug: String? = null,
    val coverHorizontal: String? = null,
    val coverVertical: String? = null,
    val storeLink: String? = null,
    val operatingSystems: List<String>? = null,
    val developers: List<String>? = null,
    val publishers: List<String>? = null,
    val genres: List<GogGenre>? = null,
    val releaseDate: String? = null,
    val storeReleaseDate: String? = null,
    val productType: String? = null,
    val galaxyBackgroundImage: String? = null,
    val screenshots: List<String>? = null,
    val reviewsRating: Int? = null
)
