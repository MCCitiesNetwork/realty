plugins {
    java
    `maven-publish`
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
    maven {
        name = "enginehub"
        url = uri("https://maven.enginehub.org/repo/")
    }

    maven {
        name = "eseentialsx"
        url = uri("https://repo.essentialsx.net/releases/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.15") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    compileOnly("net.essentialsx:EssentialsX:2.19.0") {
        exclude(group = "org.bukkit", module = "bukkit")
        exclude(group = "org.spigotmc", module = "spigot-api")
    }
    compileOnly("org.jetbrains:annotations:26.0.2-1")
    implementation("org.incendo:cloud-paper:2.0.0-beta.10")
    implementation("org.spongepowered:configurate-yaml:4.2.0")
    implementation("org.mybatis:mybatis:3.5.19")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.6")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.testcontainers:testcontainers-mariadb:2.0.1")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.2")
}


val targetJavaVersion = 21

java.toolchain.languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))

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

    shadowJar {
        val base = "io.github.md5sha256.realty.libraries"
        relocate("org.mariadb", "${base}.org.mariadb")
        relocate("org.mybatis", "${base}.org.mybatis")
        relocate("org.spongepowered", "${base}.org.spongepowered")
        relocate("org.yaml", "${base}.org.yaml")
        relocate("io.leangen.geantyref", "${base}.io.leangen.geantyref")
        relocate("org.apache.ibatis", "${base}.org.apache.ibatis")
        relocate("org.jetbrains.annotations", "${base}.org.jetbrains.annotations")
        relocate("org.intellij.lang", "${base}.org.intellij.lang")
        relocate("net.kyori.option", "${base}.net.kyori.option")
        relocate("org.incendo.cloud", "${base}.org.incendo.cloud")
    }

    publish {
        dependsOn(shadowJar)
    }

    processResources {
        filesMatching("paper-plugin.yml") {
            expand("version" to project.version)
        }
    }


    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("1.21.8")
        downloadPlugins {
            // WorldEdit 7.4.0
            url("https://mediafilez.forgecdn.net/files/7479/274/worldedit-bukkit-7.4.0.jar")
            // WorldGuard 7.0.14
            url("https://mediafilez.forgecdn.net/files/6643/567/worldguard-bukkit-7.0.14-dist.jar")
            // EssX
            url("https://ci.ender.zone/job/EssentialsX/1774/artifact/jars/EssentialsX-2.22.0-dev+74-d7452bf.jar")
            // Vault
            url("https://mediafilez.forgecdn.net/files/3007/470/Vault.jar")
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifact(tasks.jar)
            pom {
                name.set("Realty")
                description.set("PaperMC plugin for real estate economy on WorldGuard regions")
                url.set("https://github.com/MCCitiesNetwork/realty")
                developers {
                    developer {
                        id.set("md5sha256")
                        name.set("Andrew Wong")
                        email.set("42793301+md5sha256@users.noreply.github.com")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/MCCitiesNetwork/realty.git")
                    developerConnection.set("scm:git:ssh://github.com/MCCitiesNetwork/realty.git")
                    url.set("https://github.com/MCCitiesNetwork/realty")
                }
            }
        }
    }
}
