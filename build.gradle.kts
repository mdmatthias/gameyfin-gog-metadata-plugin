import java.util.jar.Manifest
import java.util.Properties

plugins {
    kotlin("jvm") version "2.2.20"
    id("com.google.devtools.ksp") version "2.2.20-2.0.3"
    kotlin("plugin.serialization") version "2.2.20"
}

group = "io.github.mdmatthias.gameyfin.plugins"
// Read version from MANIFEST.MF
val manifestFile = file("src/main/resources/MANIFEST.MF")
val manifestVersion: String? = if (manifestFile.exists()) {
    runCatching {
        Manifest(manifestFile.inputStream()).mainAttributes.getValue("Plugin-Version")
    }.getOrNull()
} else null
version = manifestVersion ?: "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    compileOnly("org.gameyfin:plugin-api:2.3.3")
    
    // PF4J
    compileOnly("org.pf4j:pf4j:3.13.0")
    ksp("care.better.pf4j:pf4j-kotlin-symbol-processing:2.2.20-1.0.3")

    compileOnly("org.slf4j:slf4j-api:2.0.9") // Provided by host

    val ktor_version = "3.1.3"
    implementation("io.ktor:ktor-client-core:$ktor_version") { exclude(group = "org.slf4j") }
    implementation("io.ktor:ktor-client-cio:$ktor_version") { exclude(group = "org.slf4j") }
    implementation("io.ktor:ktor-client-content-negotiation:$ktor_version") { exclude(group = "org.slf4j") }
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version") { exclude(group = "org.slf4j") }

    val resilience4jVersion = "2.2.0"
    implementation("io.github.resilience4j:resilience4j-ratelimiter:$resilience4jVersion") { exclude(group = "org.slf4j") }
    implementation("io.github.resilience4j:resilience4j-bulkhead:$resilience4jVersion") { exclude(group = "org.slf4j") }
    implementation("io.github.resilience4j:resilience4j-all:$resilience4jVersion") { exclude(group = "org.slf4j") }

    implementation("me.xdrop:fuzzywuzzy:1.4.0")
}

tasks.register("jarCombined", Jar::class) {
    archiveBaseName.set("gog-plugin")
    doFirst {
        val props = Properties()
        props.setProperty("gog.services", "all")
        file("${layout.buildDirectory.get().asFile}/tmp/gog-config/gog.properties").apply {
            parentFile.mkdirs()
            writer().use { props.store(it, null) }
        }
    }
    from("${layout.buildDirectory.get().asFile}/tmp/gog-config")
}

tasks.register("jarDreamlist", Jar::class) {
    archiveBaseName.set("gog-plugin-dreamlist")
    doFirst {
        val props = Properties()
        props.setProperty("gog.services", "dreamlist")
        file("${layout.buildDirectory.get().asFile}/tmp/gog-config-dreamlist/gog.properties").apply {
            parentFile.mkdirs()
            writer().use { props.store(it, null) }
        }
    }
    from("${layout.buildDirectory.get().asFile}/tmp/gog-config-dreamlist")
}

tasks.register("jarCatalog", Jar::class) {
    archiveBaseName.set("gog-plugin-catalog")
    doFirst {
        val props = Properties()
        props.setProperty("gog.services", "catalog")
        file("${layout.buildDirectory.get().asFile}/tmp/gog-config-catalog/gog.properties").apply {
            parentFile.mkdirs()
            writer().use { props.store(it, null) }
        }
    }
    from("${layout.buildDirectory.get().asFile}/tmp/gog-config-catalog")
}

tasks.withType<Jar>().configureEach {
    if (name in listOf("jarCombined", "jarDreamlist", "jarCatalog")) {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        isZip64 = true

        dependsOn("kspKotlin")

        manifest {
            attributes(
                "Plugin-Id" to when(name) {
                    "jarCombined" -> "io.github.mdmatthias.gameyfin.plugins.gog"
                    "jarDreamlist" -> "io.github.mdmatthias.gameyfin.plugins.gog-dreamlist"
                    "jarCatalog" -> "io.github.mdmatthias.gameyfin.plugins.gog-catalog"
                    else -> ""
                },
                "Plugin-Name" to when(name) {
                    "jarCombined" -> "GOG Metadata"
                    "jarDreamlist" -> "GOG Metadata (Dreamlist)"
                    "jarCatalog" -> "GOG Metadata (Catalog)"
                    else -> ""
                },
                "Plugin-Class" to when(name) {
                    "jarCombined" -> "io.github.mdmatthias.gameyfin.plugins.gog.combined.GogCombinedPlugin"
                    "jarDreamlist" -> "io.github.mdmatthias.gameyfin.plugins.gog.dreamlist.GogDreamlistPlugin"
                    "jarCatalog" -> "io.github.mdmatthias.gameyfin.plugins.gog.catalog.GogCatalogPlugin"
                    else -> ""
                },
                "Plugin-Version" to (manifestVersion ?: "1.0-SNAPSHOT"),
                "Plugin-Description" to "Fetches metadata from GOG.com",
                "Plugin-Author" to "mdmatthias",
                "Plugin-License" to "AGPL-3.0",
                "Plugin-Url" to "https://github.com/mdmatthias/gameyfin-gog-metadata-plugin"
            )
        }

        from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
            exclude("META-INF/*.SF")
            exclude("META-INF/*.DSA")
            exclude("META-INF/*.RSA")
        }

        from(sourceSets["main"].output.classesDirs) {
            // Exclude other plugin entry points
            when(name) {
                "jarCombined" -> {
                    exclude("**/dreamlist/*")
                    exclude("**/catalog/*")
                }
                "jarDreamlist" -> {
                    exclude("**/combined/*")
                    exclude("**/catalog/*")
                }
                "jarCatalog" -> {
                    exclude("**/combined/*")
                    exclude("**/dreamlist/*")
                }
            }
        }
        
        from(sourceSets["main"].resources) {
            exclude("MANIFEST.MF")
        }

        // Include KSP-generated resources, but filter extensions.idx
        from(layout.buildDirectory.get().asFile.resolve("generated/ksp/main/resources")) {
            filesMatching("META-INF/extensions.idx") {
                filter { line ->
                    val providerClass = when(this@configureEach.name) {
                        "jarCombined" -> "io.github.mdmatthias.gameyfin.plugins.gog.combined.CombinedGogMetadataProvider"
                        "jarDreamlist" -> "io.github.mdmatthias.gameyfin.plugins.gog.dreamlist.DreamlistGogMetadataProvider"
                        "jarCatalog" -> "io.github.mdmatthias.gameyfin.plugins.gog.catalog.CatalogGogMetadataProvider"
                        else -> ""
                    }
                    if (line == providerClass || line.startsWith("#")) line else null
                }
            }
        }

        // Include logo file under META-INF/resources
        from("src/main/resources") {
            include("logo.*")
            into("META-INF/resources")
        }
    }
}

tasks.named("build") {
    dependsOn("jarCombined", "jarDreamlist", "jarCatalog")
}

tasks.named("jar") {
    enabled = false
}
