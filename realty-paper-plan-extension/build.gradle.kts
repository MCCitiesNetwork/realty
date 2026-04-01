plugins {
    `java-library`
    `realty-conventions`
    id("com.gradleup.shadow") version "9.3.1"
}

dependencies {
    implementation(project(":realty-common"))
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    compileOnly("com.github.plan-player-analytics:Plan:5.7.3306")
    compileOnly("org.jetbrains:annotations:26.0.2-1")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.github.plan-player-analytics:Plan:5.7.3306")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    shadowJar {
        val base = "io.github.md5sha256.realty.plan.libraries"
        relocate("org.jetbrains.annotations", "${base}.org.jetbrains.annotations")
        relocate("org.intellij.lang", "${base}.org.intellij.lang")
    }

    processResources {
        filesMatching("paper-plugin.yml") {
            expand("version" to project.version)
        }
    }
}
