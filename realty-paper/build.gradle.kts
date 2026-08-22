plugins {
    `java-library`
    `realty-conventions`
    id("xyz.jpenilla.run-paper") version "3.0.2"
    id("com.gradleup.shadow") version "9.3.1"
}

dependencies {
    api(project(":realty-paper-api"))
    compileOnly("io.papermc.paper:paper-api:26.1.2.+")
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
    implementation("org.incendo:cloud-paper:2.0.0-beta.10")
    implementation("org.spongepowered:configurate-yaml:4.2.0")
    // Shared module system, schema migrations and formatting helpers. Deliberately NOT relocated in
    // shadowJar: module jars are compiled against these types and loaded into this plugin's class
    // loader, so the names must match.
    implementation("com.minecraftcitiesnetwork:plugin-infrastructure:1.0.0-SNAPSHOT")

    testImplementation("io.papermc.paper:paper-api:26.1.2.+")
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

        dependsOn(":realty-paper-adapters:chat-adapter:shadowJar")
        from(project(":realty-paper-adapters:chat-adapter")
                .tasks.named("shadowJar").map { it.outputs.files.singleFile }) {
            into("modules")
            rename { "chat-adapter.jar" }
        }
    }

    processResources {
        val projectVersion = version
        filesMatching("paper-plugin.yml") {
            expand("version" to projectVersion)
        }
    }

    runServer {
        // Resolve everything the doFirst needs at CONFIGURATION time. Reaching for
        // `project(...)` or `project.layout` inside the action is what made this task
        // incompatible with the configuration cache; Providers and Files serialize fine.
        val chatAdapterJar = project(":realty-paper-adapters:chat-adapter")
                .tasks.named("shadowJar", AbstractArchiveTask::class).flatMap { it.archiveFile }
        // EssentialsX is downloaded below, so stage the adapter that pairs with it too — otherwise
        // the spec's Essentials smoke test cannot be run as written.
        val essentialsAdapterJar = project(":realty-paper-adapters:essentials-adapter")
                .tasks.named("shadowJar", AbstractArchiveTask::class).flatMap { it.archiveFile }
        val moduleDir = layout.projectDirectory.dir("run/plugins/Realty/modules").asFile
        // The archiveFile providers carry their producing task as a dependency, so the
        // explicit dependsOn declarations they replace are no longer needed.
        inputs.files(chatAdapterJar, essentialsAdapterJar)
        doFirst {
            moduleDir.mkdirs()
            chatAdapterJar.get().asFile.copyTo(moduleDir.resolve("chat-adapter.jar"), overwrite = true)
            essentialsAdapterJar.get().asFile
                    .copyTo(moduleDir.resolve("essentials-adapter.jar"), overwrite = true)
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
