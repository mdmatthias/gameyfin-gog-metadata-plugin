import java.util.jar.Manifest

plugins {
    kotlin("jvm") version "2.2.20"
    id("com.google.devtools.ksp") version "2.2.20-2.0.3"
    kotlin("plugin.serialization") version "2.2.20"
}

group = "org.gameyfin.plugins"
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

dependencies {
    compileOnly(files("libs/plugin-api.jar"))
    
    // PF4J
    implementation("org.pf4j:pf4j:3.13.0")
    ksp("care.better.pf4j:pf4j-kotlin-symbol-processing:2.2.20-1.0.3")

    implementation("org.slf4j:slf4j-api:2.0.9") // Ensure SLF4J is available
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.3")

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
    implementation("org.jsoup:jsoup:1.20.1")
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    isZip64 = true
    archiveBaseName.set("gog-plugin")

    dependsOn("kspKotlin")

    manifest {
        from("./src/main/resources/MANIFEST.MF")
    }

    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
    }
    
    from(sourceSets["main"].output.classesDirs)
    from(sourceSets["main"].resources)

    // Include KSP-generated resources
    from(layout.buildDirectory.get().asFile.resolve("generated/ksp/main/resources"))

    // Include logo file under META-INF/resources
    from("src/main/resources") {
        include("logo.*")
        into("META-INF/resources")
    }
}
