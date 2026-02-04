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

## Screenshots
<img width="1927" height="1264" alt="image" src="https://github.com/user-attachments/assets/2bdd6f44-ccfd-412c-a1c2-e5fda71998bd" />
<img width="1927" height="1264" alt="image" src="https://github.com/user-attachments/assets/76b69838-fb95-4e32-bfdb-53a82af7f31a" />


## API Usage & Performance

The plugin leverages multiple GOG API endpoints to provide comprehensive metadata while maintaining high performance:

- **Catalog API (`catalog.gog.com/v1/catalog`):** Primary search endpoint for game titles, basic metadata, and store availability.
- **GamesDb API (`gamesdb.gog.com/wishlist/wishlisted_games`):** Also known as the "Dreamlist" API. This is used as a secondary search source to enrich metadata and, crucially, to allow matching for games that have been delisted from the GOG store or are not available on GOG at all.
- **V2 Games API (`api.gog.com/v2/games`):** Fetches detailed product information, including full game descriptions and developer/publisher details.

The plugin utilizes smart caching and a unified rate limiter (4 requests per second) to ensure reliable operation and prevent IP rate-limiting. This architecture allows for snappy metadata retrieval even for large libraries without the strict limitations of older GOG API endpoints.

## Installation

1.  Download the latest release jar (`gog-plugin-x.y.z.jar`) from the [Releases](https://github.com/mdmatthias/gameyfin-gog-metadata-plugin/releases) page.
2.  Navigate to your Gameyfin installation directory.
3.  Place the `.jar` file into the `plugins/` folder.
    *   *Note: If the `plugins` folder does not exist, create it.*
4.  Restart Gameyfin.

### Docker Compose

If you are running Gameyfin using Docker Compose, you can mount the plugin JAR file as a volume.
It is also recommended to increase the maximum amount of database connections to prevent full library scans blocking the whole application from loading things. (See https://github.com/gameyfin/gameyfin/issues/867 )

```yaml
services:
  gameyfin:
    # ... other configuration ...
    environment:
      # ... other env vars ...
      SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE: 50
      SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT: 60000
    
    volumes:
      - /your/location/gog-plugin-x.x.x.jar:/opt/gameyfin/plugins/gog-plugin-x.x.x.jar
```
Or if you want to automatically download the latest version when Gameyfin docker starts:
```yaml
services:
  gameyfin:
    # ... other configuration ...
    environment:
      # ... other env vars ...
      SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE: 50
      SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT: 60000
    
    volumes:
      - "./plugins:/opt/gameyfin/plugins"
    entrypoint:
      # download latest GOG metadata plugin
      - /bin/bash
      - -c
      - |
        apt-get update && apt-get install -y curl jq
        LATEST_URL=$$(curl -s https://api.github.com/repos/mdmatthias/gameyfin-gog-metadata-plugin/releases/latest | jq -r '.assets[] | select(.name | endswith(".jar")) | .browser_download_url' | head -n 1)
        echo "Downloading latest GOG plugin from $$LATEST_URL"
        curl -L -o /opt/gameyfin/plugins/gog-plugin-latest.jar "$$LATEST_URL"
        echo "Starting Gameyfin..."
        exec /entrypoint.sh
```

## Development

### Prerequisites
- JDK 21 or higher
- [Gameyfin](https://github.com/gameyfin/gameyfin) (for testing)

### Building the Plugin

This project is a standard Gradle project. To build the plugin JAR:

```bash
./gradlew build
```

The compiled plugin will be available in:
`build/libs/gog-plugin-VERSION.jar`

## License

This project is licensed under the [GNU Affero General Public License v3.0 (AGPL-3.0)](https://www.gnu.org/licenses/agpl-3.0.en.html).

---
*This plugin was developed with the assistance of AI (Gemini).*

