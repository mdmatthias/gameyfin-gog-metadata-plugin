package io.github.mdmatthias.gameyfin.plugins.gog.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
data class GogProductDetails(
    val description: JsonElement? = null,
    @SerialName("_embedded") val embedded: GogV2Embedded? = null,
    val title: String? = null,
    val id: Int? = null
) {
    val descriptionText: String?
        get() {
            return when (description) {
                is JsonPrimitive -> description.contentOrNull
                is JsonObject -> description["full"]?.let { (it as? JsonPrimitive)?.contentOrNull } 
                              ?: description["lead"]?.let { (it as? JsonPrimitive)?.contentOrNull }
                else -> null
            }
        }
}

@Serializable
data class GogV2Embedded(
    val product: GogV2Product? = null
)

@Serializable
data class GogV2Product(
    val title: String? = null
)

@Serializable
data class GogTag(
    val name: String
)

@Serializable
data class GogFeature(
    val name: String
)

@Serializable
data class GogGenre(
    val name: String
)

@Serializable
data class GogImages(
    val background: String? = null,
    val logo: String? = null,
    val logo2x: String? = null,
)

@Serializable
data class GogSystemCompatibility(
    val windows: Boolean = false,
    val osx: Boolean = false,
    val linux: Boolean = false
)

@Serializable
data class GogDescription(
    val lead: String? = null,
    val full: String? = null
)

@Serializable
data class GogScreenshot(
    val formatted_images: List<GogFormattedImage>? = null
)

@Serializable
data class GogFormattedImage(
    val formatter_name: String? = null,
    val image_url: String? = null
)
