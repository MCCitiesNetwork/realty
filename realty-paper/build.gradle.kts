plugins {
    `java-library`
    `realty-conventions`
    `maven-publish`
    id("xyz.jpenilla.run-paper") version "2.3.1"
    id("com.gradleup.shadow") version "9.3.1"
}

dependencies {
    api(project(":realty-common"))
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

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {

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
