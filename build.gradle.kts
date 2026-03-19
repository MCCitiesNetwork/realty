plugins {
    java
}

group = "io.github.md5sha256"
version = "1.0.0-SNAPSHOT"

val targetJavaVersion = 21

subprojects {
    apply(plugin = "java")

    group = rootProject.group
    version = rootProject.version

    java.toolchain.languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))

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
        maven {
            name = "enginehub"
            url = uri("https://maven.enginehub.org/repo/")
        }
        maven {
            name = "essentialsx"
            url = uri("https://repo.essentialsx.net/releases/")
        }
    }

    tasks {
        test {
            useJUnitPlatform()
        }

        withType(JavaCompile::class) {
            options.release.set(targetJavaVersion)
            options.encoding = "UTF-8"
            options.isFork = true
            options.isDeprecation = true
        }
    }
}
