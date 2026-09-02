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

tasks.shadowJar {
    archiveBaseName.set("realty-rest")
    archiveClassifier.set("all")
    manifest {
        attributes["Main-Class"] = "io.github.md5sha256.realty.rest.RealtyRestMain"
    }
    mergeServiceFiles()
}
