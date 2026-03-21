plugins {
    `realty-conventions`
    id("com.gradleup.shadow") version "9.3.1"
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
}