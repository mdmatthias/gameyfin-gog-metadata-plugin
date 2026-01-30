# Gameyfin GOG Plugin

A metadata provider plugin for [Gameyfin](https://github.com/gameyfin/gameyfin) that fetches game information and artwork from the GOG (Good Old Games) store.

## Features

- **Metadata Retrieval:** Automatically fetches game details including:
  - Game Title and Description
  - Release Date
  - Developers and Publishers
  - Genres and Tags
  - User Ratings
- **Artwork:** Downloads high-quality assets:
  - Box art / Covers
  - Background headers / Banners
  - Screenshots
- **Platform Support:** Correctly identifies platform compatibility (Windows, Linux, macOS).
- **Resilience:** Built-in rate limiting and concurrency management to prevent API blocking.

## Installation

1.  Download the latest release jar (`gog-plugin-x.y.z.jar`) from the [Releases](https://github.com/mdmatthias/gameyfin-gog-metadata-plugin/releases) page.
2.  Navigate to your Gameyfin installation directory.
3.  Place the `.jar` file into the `plugins/` folder.
    *   *Note: If the `plugins` folder does not exist, create it.*
4.  Restart Gameyfin.

### Docker Compose

If you are running Gameyfin using Docker Compose, you can mount the plugin JAR file as a volume:

```yaml
services:
  gameyfin:
    # ... other configuration ...
    volumes:
      - /your/location/gog-plugin-x.x.x.jar:/opt/gameyfin/plugins/gog-plugin-x.x.x.jar
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

