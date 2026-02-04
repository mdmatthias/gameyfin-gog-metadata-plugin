package io.github.mdmatthias.gameyfin.plugins.gog.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GogGamesDbResponse(
    val items: List<GogGamesDbItem> = emptyList()
)

@Serializable
data class GogGamesDbItem(
    val id: String,
    val title: String,
    val slug: String? = null,
    @SerialName("first_release_date") val firstReleaseDate: String? = null,
    val developers: List<GogGamesDbEntity>? = null,
    val publishers: List<GogGamesDbEntity>? = null,
    val genres: List<GogGamesDbEntity>? = null,
    val themes: List<GogGamesDbEntity>? = null,
    @SerialName("game_modes") val gameModes: List<GogGamesDbEntity>? = null,
    val summary: Map<String, String>? = null,
    val screenshots: List<GogGamesDbScreenshot>? = null,
    val cover: GogGamesDbImage? = null,
    @SerialName("vertical_cover") val verticalCover: GogGamesDbImage? = null,
    val background: GogGamesDbImage? = null,
    @SerialName("horizontal_artwork") val horizontal_artwork: GogGamesDbImage? = null
)

@Serializable
data class GogGamesDbEntity(
    val name: String
)

@Serializable
data class GogGamesDbScreenshot(
    @SerialName("url_format") val urlFormat: String
)

@Serializable
data class GogGamesDbImage(
    @SerialName("url_format") val urlFormat: String
)
