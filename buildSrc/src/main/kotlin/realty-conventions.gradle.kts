plugins {
    java
}

group = "io.github.md5sha256"
version = "1.2.0-SNAPSHOT"

val targetJavaVersion = 21

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

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
