package io.github.mdmatthias.gameyfin.plugins.gog.catalog

import io.github.mdmatthias.gameyfin.plugins.gog.GogPlugin
import io.github.mdmatthias.gameyfin.plugins.gog.GogMetadataService
import org.gameyfin.pluginapi.gamemetadata.GameMetadata
import org.gameyfin.pluginapi.gamemetadata.GameMetadataProvider
import org.gameyfin.pluginapi.gamemetadata.Platform
import org.pf4j.Extension
import org.pf4j.PluginWrapper

class GogCatalogPlugin(wrapper: PluginWrapper) : GogPlugin(wrapper)

@Suppress("Unused")
@Extension(ordinal = 3)
class CatalogGogMetadataProvider : GameMetadataProvider {
    override val supportedPlatforms: Set<Platform> = GogMetadataService.supportedPlatforms
    override fun fetchByTitle(gameTitle: String, platformFilter: Set<Platform>, maxResults: Int): List<GameMetadata> =
        GogMetadataService.fetchByTitle(gameTitle, platformFilter, maxResults)
    override fun fetchById(id: String): GameMetadata? = GogMetadataService.fetchById(id)
}
