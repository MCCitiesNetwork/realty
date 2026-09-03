plugins {
    `java-library`
    `realty-conventions`
    id("com.gradleup.shadow") version "9.3.1"
}

dependencies {
    compileOnly(project(":realty-paper"))
    compileOnly(project(":realty-paper-api"))
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.74-stable")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.18") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    compileOnly("org.jetbrains:annotations:26.0.2-1")
    compileOnly("com.minecraftcitiesnetwork:plugin-infrastructure:1.0.0-SNAPSHOT")
    // Paper ships slf4j-api bound to Log4j; shading our own copy would leave Javalin logging
    // into a binding-less void.
    compileOnly("org.slf4j:slf4j-api:2.0.16")

    implementation("io.javalin:javalin:6.4.0") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")

    testImplementation(project(":realty-paper-api"))
    testImplementation("io.papermc.paper:paper-api:26.1.2.build.74-stable")
    testImplementation("com.sk89q.worldguard:worldguard-bukkit:7.0.18") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    testImplementation("io.javalin:javalin-testtools:6.4.0")
    testImplementation("org.slf4j:slf4j-simple:2.0.16")
}

tasks.shadowJar {
    archiveBaseName.set("query-service")
    archiveClassifier.set("")
    val base = "io.github.md5sha256.realty.adapter.query.libraries"
    relocate("io.javalin", "$base.io.javalin")
    relocate("org.eclipse.jetty", "$base.org.eclipse.jetty")
    relocate("com.fasterxml.jackson", "$base.com.fasterxml.jackson")
    relocate("kotlin", "$base.kotlin")
    relocate("org.intellij", "$base.org.intellij")
    relocate("org.jetbrains", "$base.org.jetbrains")
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
