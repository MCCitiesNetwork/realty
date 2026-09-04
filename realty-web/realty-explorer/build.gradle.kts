import com.github.gradle.node.npm.task.NpmTask

plugins {
    base
    id("com.github.node-gradle.node") version "7.1.0"
}

node {
    // Pinned so CI does not drift with whatever Node the runner happens to ship.
    version.set("22.18.0")
    download.set(true)
}

val npmBuild by tasks.registering(NpmTask::class) {
    dependsOn(tasks.named("npmInstall"))
    npmCommand.set(listOf("run", "build"))
    inputs.dir("src")
    inputs.file("index.html")
    inputs.file("package.json")
    inputs.file("tsconfig.json")
    inputs.file("vite.config.ts")
    // The spec lives in a sibling module. Without declaring it, editing the API and
    // rebuilding would quietly reuse a stale client.
    inputs.file("../realty-rest/src/main/resources/openapi.yaml")
    outputs.dir(layout.projectDirectory.dir("dist"))
}

val npmTest by tasks.registering(NpmTask::class) {
    dependsOn(tasks.named("npmInstall"))
    npmCommand.set(listOf("run", "test"))
    inputs.dir("src")
    inputs.file("package.json")
    // Vitest reports nothing Gradle can treat as an output, so an up-to-date check
    // here would mean "tests silently skipped" rather than "tests passed".
    outputs.upToDateWhen { false }
}

tasks.named("assemble") { dependsOn(npmBuild) }
tasks.named("check") { dependsOn(npmTest) }
