plugins {
    `realty-conventions`
    id("com.gradleup.shadow") version "9.3.1"
}

dependencies {
    implementation(project(":realty-backend"))
    implementation("io.javalin:javalin:6.4.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2")
    implementation("org.slf4j:slf4j-simple:2.0.16")
    compileOnly("org.jetbrains:annotations:26.0.2-1")

    testImplementation("io.javalin:javalin-testtools:6.4.0")
}

tasks.test {
    // PterodactylEggTest reads pterodactyl-egg.json from the project directory
    // rather than the classpath, so Gradle would not otherwise see it as an input:
    // editing only the egg would leave the task UP-TO-DATE and report a stale pass
    // on the very file the test exists to guard.
    inputs.file("pterodactyl-egg.json")
        .withPropertyName("pterodactylEgg")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

tasks.shadowJar {
    archiveBaseName.set("realty-rest")
    archiveClassifier.set("all")
    manifest {
        attributes["Main-Class"] = "io.github.md5sha256.realty.rest.RealtyRestMain"
    }
    mergeServiceFiles()
}
