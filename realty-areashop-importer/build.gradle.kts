plugins {
    `realty-conventions`
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    implementation("com.github.md5sha256.AreaShop:areashop:cc3a0b32a4") {
        exclude(group = "org.bukkit")
        exclude(group = "org.spigotmc")
        exclude(group = "com.sk89q.worldedit")
        exclude(group = "com.sk89q.worldguard")
    }
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.15") {
        exclude(group = "org.bukkit")
        exclude(group = "org.spigotmc")
    }
    implementation(project(":realty-paper"))
}

tasks {
    jar
}