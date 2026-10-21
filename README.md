# Gameyfin GOG Plugin

A metadata provider plugin for [Gameyfin](https://github.com/gameyfin/gameyfin) that fetches game information and artwork from the GOG (Good Old Games) store.

## Features

- **Metadata Retrieval:** Automatically fetches game details including:
  - Game Title and Description
  - Release Date
  - Developers and Publishers
  - Categorization: Maps GOG genres and tags to Gameyfin Genres, Themes, and Features (e.g., Singleplayer, Controller Support)
  - User Ratings
- **Artwork:** Downloads high-quality assets:
  - Box art / Covers
  - Background headers / Banners
  - Screenshots
- **Platform Support:** Correctly identifies platform compatibility (Windows, Linux, macOS).
- **Performance:** Optimized search flow and ID-based caching to ensure a snappy user experience.


## API Usage & Performance

The plugin leverages multiple GOG API endpoints to provide comprehensive metadata while maintaining high performance:

- **Catalog API (`catalog.gog.com/v1/catalog`):** Primary search endpoint for game titles, basic metadata, and store availability.
- **GamesDb API (`gamesdb.gog.com/wishlist/wishlisted_games`):** Also known as the "Dreamlist" API. This is used as a secondary search source to enrich metadata and, crucially, to allow matching for games that have been delisted from the GOG store or are not available on GOG at all.
- **V2 Games API (`api.gog.com/v2/games`):** Fetches detailed product information, including full game descriptions and developer/publisher details.

The plugin utilizes smart caching and a unified rate limiter (4 requests per second) to ensure reliable operation and prevent IP rate-limiting. This architecture allows for snappy metadata retrieval even for large libraries without the strict limitations of older GOG API endpoints.

## Plugin Variants

Starting from version 0.0.7, the GOG plugin is provided in three different variants (JARs) to give users more flexibility in how they retrieve metadata:

1.  **Combined (`gog-plugin-0.0.7.jar`):** This is the default variant (previously named `gog-plugin-x.y.z.jar`). It searches both the GOG Catalog and the GOG GamesDb (Dreamlist) APIs and merges the results into a single list (up to 10 results).
2.  **Catalog Only (`gog-plugin-catalog-0.0.7.jar`):** This variant only searches the GOG Catalog API. Use this if you want cleaner, high-quality results for games actively sold on GOG and prefer using other plugins (like Steam or IGDB) for your non-GOG or delisted games.
3.  **Dreamlist Only (`gog-plugin-dreamlist-0.0.7.jar`):** This variant only searches the GOG GamesDb (Dreamlist) API. This source is excellent for finding delisted or obscure games, but it can be **"noisy"** and may occasionally return duplicate entries or slightly less accurate matches.

### Why use separate JARs?
Each plugin in Gameyfin returns a maximum of 10 results per search. 

- **Maximum Coverage:** By installing **both** the `catalog` and `dreamlist` JARs separately, you can get up to 20 results (10 from each). This is the best setup if you want to ensure a match is found even for the most obscure titles.
- **Tailored Experience:** If you find the Dreamlist results too cluttered, you can simply install the `catalog` JAR. This ensures that your GOG results are always "clean," while you rely on other metadata providers for the rest of your library.

**Important:** Do not install the `combined` JAR at the same time as the `catalog` or `dreamlist` JARs, as this will result in duplicate search results.

## Installation

### Gameyfin plugin marketplace (recommended)

Requires Gameyfin 2.5.0 or newer.

1.  Go to *Administration → Plugins → Marketplace*.
2.  Install **one** of the setups described below: *GOG Metadata* (combined), or *GOG Metadata (Catalog)* and/or *GOG Metadata (Dreamlist)*.
3.  Marketplace plugins are third-party plugins, so they are installed **disabled**. Enable them in the plugin overview.

### Manual installation

1.  Choose your preferred setup from the [Releases](https://github.com/mdmatthias/gameyfin-gog-metadata-plugin/releases) page:
    *   **Integrated (Default):** Download `gog-plugin-0.0.7.jar`. This gives you one plugin entry in Gameyfin with combined results (up to 10).
    *   **Maximum Results:** Download **both** `gog-plugin-catalog-0.0.7.jar` and `gog-plugin-dreamlist-0.0.7.jar`. This gives you two separate plugin entries in Gameyfin, allowing for up to 20 search results total.
    *   **Standalone Specific:** Download **either** `gog-plugin-catalog-0.0.7.jar` or `gog-plugin-dreamlist-0.0.7.jar`. Use this if you only want results from one specific API (e.g., if you only want the "clean" Catalog results).
2.  Navigate to your Gameyfin installation directory.
3.  Place the selected `.jar` file(s) into the `plugins/` folder.
    *   *Note: If the `plugins` folder does not exist, create it.*
4.  Restart Gameyfin.

### Docker Compose

If you are running Gameyfin using Docker Compose, you can mount the plugin JAR file as a volume.
It is also recommended to increase the maximum amount of database connections to prevent full library scans blocking the whole application from loading things. (See https://github.com/gameyfin/gameyfin/issues/867 )

```yaml
services:
  gameyfin:
    # ... other configuration ...
    volumes:
      - /your/location/gog-plugin-0.0.7.jar:/opt/gameyfin/plugins/gog-plugin-0.0.7.jar
```

Or if you want to automatically download the latest version when Gameyfin docker starts (example for the combined version):
```yaml
services:
  gameyfin:
    # ... other configuration ...
    volumes:
      - "./plugins:/opt/gameyfin/plugins"
    entrypoint:
      # download latest GOG metadata plugin (combined variant)
      - /bin/bash
      - -c
      - |
        apt-get update && apt-get install -y curl jq
        LATEST_URL=$$(curl -s https://api.github.com/repos/mdmatthias/gameyfin-gog-metadata-plugin/releases/latest | jq -r '.assets[] | select(.name | (contains("combined") or (contains("gog-plugin") and (contains("catalog") | not) and (contains("dreamlist") | not))) and endswith(".jar")) | .browser_download_url' | head -n 1)
        echo "Downloading latest GOG plugin from $$LATEST_URL"
        curl -L -o /opt/gameyfin/plugins/gog-plugin-latest.jar "$$LATEST_URL"
        echo "Starting Gameyfin..."
        exec /entrypoint.sh
```

## Development

### Prerequisites
- JDK 25 or higher
- [Gameyfin](https://github.com/gameyfin/gameyfin) (for testing)

### Building the Plugin

This project is a standard Gradle project. To build all plugin variants:

```bash
./gradlew build
```

The compiled plugin variants will be available in `build/libs/`:
- `gog-plugin-VERSION.jar` (Combined)
- `gog-plugin-catalog-VERSION.jar`
- `gog-plugin-dreamlist-VERSION.jar`

The build is reproducible: rebuilding the same commit gives JARs with the same SHA-512 checksum.

### Releasing & publishing to the marketplace

The three variants are listed in the [Gameyfin plugin marketplace](https://github.com/gameyfin/plugin-marketplace) as separate plugins. Their metadata (name, description, logo, tags, ...) lives in [`marketplace/`](marketplace).

1.  Bump `Plugin-Version` in `src/main/resources/MANIFEST.MF` (and `Plugin-Requires` if the minimum Gameyfin version changes).
2.  Commit, then tag and push: `git tag vX.Y.Z && git push origin vX.Y.Z`.
3.  The release workflow builds the JARs, attaches them with their `.sha512` checksums and generates the marketplace manifests
    (`io.github.mdmatthias.gameyfin.plugins.gog*.json`, including all previously published releases). It validates them with the marketplace's own `validate.py`.
4.  Copy the three manifest JSON files from the GitHub release into `plugins/` of your fork of the marketplace and open a pull request.

Published JARs must never be replaced, because the marketplace pins their checksum. Publish a new version instead.

To generate the manifests locally: `python3 scripts/marketplace_manifests.py --version X.Y.Z --existing ../plugin-marketplace/plugins --out ../plugin-marketplace/plugins`.

## License

This project is licensed under the [GNU Affero General Public License v3.0 (AGPL-3.0)](https://www.gnu.org/licenses/agpl-3.0.en.html).

## Screenshots
<img width="1927" height="1264" alt="image" src="https://github.com/user-attachments/assets/2bdd6f44-ccfd-412c-a1c2-e5fda71998bd" />
<img width="1927" height="1264" alt="image" src="https://github.com/user-attachments/assets/76b69838-fb95-4e32-bfdb-53a82af7f31a" />


---
*This plugin was developed with the assistance of AI (Gemini).*

