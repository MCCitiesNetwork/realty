plugins {
    `java-library`
    `realty-conventions`
    id("com.gradleup.shadow") version "9.3.1"
}

dependencies {
    compileOnly(project(":realty-backend"))
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    compileOnly("com.github.plan-player-analytics:Plan:5.7.3306")
    compileOnly("org.jetbrains:annotations:26.0.2-1")

    testImplementation(project(":realty-backend"))
    testImplementation("com.github.plan-player-analytics:Plan:5.7.3306")
}

tasks {
    shadowJar {
        val base = "io.github.md5sha256.realty.libraries"
        relocate("org.jetbrains.annotations", "${base}.org.jetbrains.annotations")
        relocate("org.intellij.lang", "${base}.org.intellij.lang")
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

    processResources {
        filesMatching("paper-plugin.yml") {
            expand("version" to project.version)
        }
    }
}
