plugins {
    java
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "io.github.md5sha256"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        name = "papermc-repo"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
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
