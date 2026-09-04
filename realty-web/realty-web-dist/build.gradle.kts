plugins {
    `realty-conventions`
    id("com.gradleup.shadow") version "9.3.1"
}

dependencies {
    implementation(project(":realty-web:realty-rest"))
}

// The explorer's build output becomes resources under /web, so the single artifact
// carries the front end and Javalin serves it straight from the classpath. One jar,
// one process -- which is what lets this run under a single Pterodactyl egg, where
// two processes cannot be supervised.
val copyExplorer by tasks.registering(Copy::class) {
    dependsOn(":realty-web:realty-explorer:npmBuild")
    from(project(":realty-web:realty-explorer").layout.projectDirectory.dir("dist"))
    into(layout.buildDirectory.dir("explorer/web"))
}

sourceSets.main {
    resources.srcDir(layout.buildDirectory.dir("explorer"))
}

tasks.named("processResources") { dependsOn(copyExplorer) }

tasks.shadowJar {
    archiveBaseName.set("realty-web-dist")
    archiveClassifier.set("all")
    manifest {
        attributes["Main-Class"] = "io.github.md5sha256.realty.dist.RealtyWebDistMain"
    }
    mergeServiceFiles()
}
