plugins {
    java
    id("xyz.jpenilla.run-paper") version "2.3.1"
    id("com.gradleup.shadow") version "9.3.1"
}

group = "io.github.md5sha256"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        name = "papermc-repo"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        name = "jitpack"
        url = uri("https://jitpack.io")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7")
    compileOnly("org.jetbrains:annotations:26.0.2-1")
    implementation("org.spongepowered:configurate-yaml:4.2.0")
}


val targetJavaVersion = 21

java.toolchain.languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))

tasks {

    withType(JavaCompile::class) {
        options.release.set(targetJavaVersion)
        options.encoding = "UTF-8"
        options.isFork = true
        options.isDeprecation = true
    }

    shadowJar {
        val base = "io.github.md5sha256.realty.libraries"
        relocate("org.spongepowered.configurate", "${base}.org.spongepowered.configurate")
    }

    processResources {
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }


    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin"s jar (or shadowJar if present) will be used automatically.
        minecraftVersion("1.21.8")
    }
}
