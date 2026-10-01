plugins {
    id("dev.kikugie.stonecutter")
    id("fabric-loom") version "1.14.10" apply false
}

stonecutter active "1.21.11"

stonecutter parameters {
    replacements {
        // Mojang renamed ResourceLocation to Identifier in 1.21.11.
        string(current.parsed < "1.21.11") {
            replace("Identifier", "ResourceLocation")
        }
    }
}
