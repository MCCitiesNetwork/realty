plugins {
    `realty-conventions`
    `realty-publish`
}

dependencies {
    api(project(":realty-backend"))
    compileOnlyApi("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    compileOnlyApi("com.sk89q.worldguard:worldguard-bukkit:7.0.15") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    compileOnlyApi("org.jetbrains:annotations:26.0.2-1")
    api("org.spongepowered:configurate-yaml:4.2.0")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("Realty Paper API")
                description.set("Paper-specific API for Realty plugin integration")
            }
        }
    }
}
