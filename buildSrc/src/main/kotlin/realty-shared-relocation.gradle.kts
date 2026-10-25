plugins {
    id("com.gradleup.shadow")
}

// Relocations that must be identical in realty-paper and in every module jar. Realty shades these
// libraries, and module jars load into its class loader while referencing the same types without
// bundling them. Shadow rewrites the references in each project's own classes (string constants
// included), so applying this one rule on both sides keeps the names in step.
//
// A module built outside this repository must apply the same relocation, or it will fail to load
// with NoClassDefFoundError.
tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    relocate(
            "com.minecraftcitiesnetwork.pluginInfrastructure",
            "io.github.md5sha256.realty.libraries.com.minecraftcitiesnetwork.pluginInfrastructure"
    )
}
