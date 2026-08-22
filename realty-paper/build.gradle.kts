plugins {
    `java-library`
    `realty-conventions`
    id("xyz.jpenilla.run-paper") version "3.0.2"
    id("com.gradleup.shadow") version "9.3.1"
}

dependencies {
    api(project(":realty-paper-api"))
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.74-stable")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.18") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    implementation("org.enginehub:squirrelid:0.3.2") {
        isTransitive = false
    }
    implementation("org.xerial:sqlite-jdbc:3.46.1.0") {
        isTransitive = false
    }
    compileOnly("net.democracycraft:treasury-api:2.0.0")
    compileOnly("org.jetbrains:annotations:26.0.2-1")
    implementation("org.incendo:cloud-paper:2.0.0")
    implementation("org.spongepowered:configurate-yaml:4.2.0")
    // Shared module system, schema migrations and formatting helpers. Deliberately NOT relocated in
    // shadowJar: module jars are compiled against these types and loaded into this plugin's class
    // loader, so the names must match.
    implementation("com.minecraftcitiesnetwork:plugin-infrastructure:1.0.0-SNAPSHOT")

    testImplementation("io.papermc.paper:paper-api:26.1.2.build.74-stable")
    testImplementation("net.democracycraft:treasury-api:2.0.0")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.23.0")
    testImplementation("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    testImplementation("com.sk89q.worldguard:worldguard-bukkit:7.0.18") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
}

// --- Development database (see compose.dev.yml) ---------------------------------
//
// Everything the task actions touch is resolved HERE, at configuration time, and
// captured as plain Files/Strings so the tasks stay configuration-cache clean.

val devComposeFile = rootProject.layout.projectDirectory.file("compose.dev.yml").asFile
val devDbConfigFile = layout.projectDirectory.file("run/plugins/Realty/database.yml").asFile
val devDbUrl = "mariadb://localhost:3306/realty"
val devDbUser = "realty"
val devDbPassword = "realty"

// Builds a `docker compose` command line. Deliberately a PURE function returning a
// list: a task action defined inside a script-level function captures the script
// itself, which the configuration cache cannot serialize. Each task registers its
// own doLast below, capturing only local values.
fun dockerCompose(vararg args: String): List<String> =
        listOf("docker", "compose", "-f", devComposeFile.absolutePath) + args

// Message used when the compose invocation fails, so the cause is obvious rather
// than a bare non-zero exit code.
val dockerHint = "Is Docker installed and running? " +
        "The dev server needs it for the MariaDB in ${devComposeFile.name}."

val startDevDb = tasks.register<Exec>("startDevDb") {
    group = "realty dev"
    description = "Starts the development MariaDB and points run/plugins/Realty/database.yml at it."
    // --wait blocks until the healthcheck passes, so the server never races a
    // MariaDB whose port is open but which cannot yet serve queries.
    commandLine(dockerCompose("up", "-d", "--wait"))
    isIgnoreExitValue = true
    val result = executionResult
    val hint = dockerHint
    val configFile = devDbConfigFile
    val url = devDbUrl
    val user = devDbUser
    val password = devDbPassword
    doLast {
        val exit = result.get().exitValue
        if (exit != 0) {
            throw GradleException("`docker compose up` exited with $exit. $hint")
        }
        // Only fill in a missing or blank config. Never clobber real credentials
        // someone has pointed at their own database.
        val existing = if (configFile.isFile) configFile.readText() else ""
        val hasUrl = Regex("""^\s*url:\s*(?!['"]?\s*$).+""", RegexOption.MULTILINE).containsMatchIn(existing)
        if (hasUrl) {
            logger.lifecycle("Dev database up; leaving existing ${configFile.name} untouched.")
        } else {
            configFile.parentFile.mkdirs()
            configFile.writeText(
                    """
                    url: '$url'
                    username: '$user'
                    password: '$password'
                    """.trimIndent() + System.lineSeparator()
            )
            logger.lifecycle("Dev database up; wrote ${configFile.name} pointing at $url")
        }
    }
}

tasks.register<Exec>("stopDevDb") {
    group = "realty dev"
    description = "Stops the development MariaDB, keeping its data."
    commandLine(dockerCompose("down"))
    isIgnoreExitValue = true
    val result = executionResult
    val hint = dockerHint
    doLast {
        val exit = result.get().exitValue
        if (exit != 0) {
            throw GradleException("`docker compose down` exited with $exit. $hint")
        }
    }
}

tasks.register<Exec>("resetDevDb") {
    group = "realty dev"
    description = "Stops the development MariaDB and DELETES its data volume."
    // Realty re-runs its migrations on enable, so the next start rebuilds the schema.
    commandLine(dockerCompose("down", "-v"))
    isIgnoreExitValue = true
    val result = executionResult
    val hint = dockerHint
    doLast {
        val exit = result.get().exitValue
        if (exit != 0) {
            throw GradleException("`docker compose down -v` exited with $exit. $hint")
        }
    }
}

tasks {

    test {
        val byteBuddyAgent = configurations.testRuntimeClasspath.get().files.find { it.name.contains("byte-buddy-agent") }
        if (byteBuddyAgent != null) {
            jvmArgs("-javaagent:$byteBuddyAgent")
        }
    }

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
        relocate("org.enginehub.squirrelid", "${base}.org.enginehub.squirrelid")
        relocate("org.sqlite", "${base}.org.sqlite")
        mergeServiceFiles()
    }

    processResources {
        val projectVersion = version
        // Declared as an input so a version bump invalidates the task. Without this Gradle only
        // hashes paper-plugin.yml itself, finds it unchanged, and reuses the previously-expanded
        // output -- shipping a jar whose manifest announces the *previous* version.
        inputs.property("version", projectVersion)
        filesMatching("paper-plugin.yml") {
            expand("version" to projectVersion)
        }
    }

    runServer {
        // Bring up (and wait for) the dev MariaDB before the server starts.
        dependsOn(startDevDb)
        // Resolve everything the doFirst needs at CONFIGURATION time. Reaching for
        // `project(...)` or `project.layout` inside the action is what made this task
        // incompatible with the configuration cache; Providers and Files serialize fine.
        val chatAdapterJar = project(":realty-paper-adapters:chat-adapter")
                .tasks.named("shadowJar", AbstractArchiveTask::class).flatMap { it.archiveFile }
        // EssentialsX is downloaded below, so stage the adapter that pairs with it too — otherwise
        // the spec's Essentials smoke test cannot be run as written.
        val essentialsAdapterJar = project(":realty-paper-adapters:essentials-adapter")
                .tasks.named("shadowJar", AbstractArchiveTask::class).flatMap { it.archiveFile }
        // PlayerNotifications is not downloaded by runServer, so player-notifications-adapter will fail to
        // initialize there with its "PlayerNotifications is not installed" error. Staging it
        // anyway keeps the jar fresh for a server that does have PN dropped in by hand.
        val playerNotificationsAdapterJar = project(":realty-paper-adapters:player-notifications-adapter")
                .tasks.named("shadowJar", AbstractArchiveTask::class).flatMap { it.archiveFile }
        // Plan is downloaded below, so stage the extension that pairs with it. Staging it
        // here rather than leaving a hand-copied jar in run/plugins is what stops it going
        // stale: the copy that lived there was built before RealtyApi became RealtyBackend
        // and failed every startup with NoClassDefFoundError.
        val planExtensionJar = project(":realty-paper-plan-extension")
                .tasks.named("shadowJar", AbstractArchiveTask::class).flatMap { it.archiveFile }
        val moduleDir = layout.projectDirectory.dir("run/plugins/Realty/modules").asFile
        val pluginsDir = layout.projectDirectory.dir("run/plugins").asFile
        // The archiveFile providers carry their producing task as a dependency, so the
        // explicit dependsOn declarations they replace are no longer needed.
        inputs.files(chatAdapterJar, essentialsAdapterJar, playerNotificationsAdapterJar, planExtensionJar)
        doFirst {
            moduleDir.mkdirs()
            chatAdapterJar.get().asFile.copyTo(moduleDir.resolve("chat-adapter.jar"), overwrite = true)
            essentialsAdapterJar.get().asFile
                    .copyTo(moduleDir.resolve("essentials-adapter.jar"), overwrite = true)
            playerNotificationsAdapterJar.get().asFile.copyTo(moduleDir.resolve("player-notifications-adapter.jar"), overwrite = true)
            // Fixed filename, so a rebuild replaces the jar instead of leaving the previous
            // version behind as a second, duplicate plugin.
            pluginsDir.mkdirs()
            planExtensionJar.get().asFile
                    .copyTo(pluginsDir.resolve("realty-paper-plan-extension.jar"), overwrite = true)
        }
        minecraftVersion("26.1.2")
        downloadPlugins {
            // WorldEdit 7.4.5 (supports 1.21.4-26.2)
            url("https://cdn.modrinth.com/data/1u6JkXh5/versions/F5ea2ov3/worldedit-bukkit-7.4.5.jar")
            // WorldGuard 7.0.18 (supports 26.1-26.2) -- matches the compileOnly version
            url("https://cdn.modrinth.com/data/DKY9btbd/versions/btHBavWa/worldguard-bukkit-7.0.18.jar")
            // EssX 2.22.0 release (the previous dev build fails to enable on 26.x)
            url("https://cdn.modrinth.com/data/hXiIvTyT/versions/nY6VN1XH/EssentialsX-2.22.0.jar")
            // Vault
            url("https://mediafilez.forgecdn.net/files/3007/470/Vault.jar")
        }
    }
}
