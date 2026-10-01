pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
}

// One jar per group of Minecraft versions that share the APIs the mod uses. Each is named after,
// and compiled against, the oldest version in its group. The groups are in stonecutter.properties.toml.
stonecutter {
    create(rootProject) {
        versions("1.21", "1.21.2", "1.21.5", "1.21.6", "1.21.9", "1.21.11")
        vcsVersion = "1.21.11"
    }
}

rootProject.name = "curvegen"
