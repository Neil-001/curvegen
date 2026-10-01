plugins {
    id("fabric-loom")
}

version = "${sc.properties.get<String>("mod.version")}+mc${sc.current.version}"
base.archivesName = "curvegen"

repositories {
    mavenCentral()
}

loom {
    splitEnvironmentSourceSets()
    mods {
        register("curvegen") {
            sourceSet(sourceSets["main"])
            sourceSet(sourceSets["client"])
        }
    }
    runConfigs.all {
        runDir = "../../run"   // one run directory for every version
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${sc.properties.get<String>("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${sc.properties.get<String>("deps.fabric_api")}")

    // Tests cover the pure-Java core (dev.curvegen.core) and don't need Minecraft running.
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// Only the 1.21.9 jar has a mixin (see LevelRendererMixin). The others drop its config.
val hasMixin = sc.current.version == "1.21.9"

tasks.processResources {
    val props = mapOf("version" to project.version.toString(), "minecraft" to sc.properties.get<String>("mod.mc_compat"))
    val mixin = hasMixin
    inputs.properties(props)
    inputs.property("mixin", mixin)
    filesMatching("fabric.mod.json") {
        expand(props)
        if (!mixin) filter { line -> if (line.contains("\"mixins\"")) null else line }
    }
}

tasks.named<ProcessResources>("processClientResources") {
    val mixin = hasMixin
    inputs.property("mixin", mixin)
    if (!mixin) exclude("curvegen.client.mixins.json")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

// Copies this version's jar to the root build/libs, so all six end up in one place.
tasks.register<Copy>("buildAndCollect") {
    group = "build"
    from(tasks.remapJar.flatMap { it.archiveFile })
    into(rootProject.layout.buildDirectory.dir("libs"))
    dependsOn("build")
}
