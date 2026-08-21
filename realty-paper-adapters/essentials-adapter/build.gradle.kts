plugins {
    `java-library`
    `realty-conventions`
    id("com.gradleup.shadow") version "9.3.1"
}

dependencies {
    compileOnly(project(":realty-paper"))
    compileOnly(project(":realty-paper-api"))
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains:annotations:26.0.2-1")
    compileOnly("com.minecraftcitiesnetwork:plugin-infrastructure:1.0.0-SNAPSHOT")
    compileOnly("net.essentialsx:EssentialsX:2.21.2") {
        exclude(group = "org.bukkit", module = "bukkit")
        exclude(group = "org.spigotmc", module = "spigot-api")
    }

    testImplementation(project(":realty-paper-api"))
    testImplementation("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
}
