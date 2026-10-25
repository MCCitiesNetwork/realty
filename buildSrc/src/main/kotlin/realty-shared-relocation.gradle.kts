plugins {
    id("com.gradleup.shadow")
}

// Every library realty-paper shades, relocated identically in realty-paper and in every module jar.
// Module jars load into Realty's class loader and reference these types without bundling them, so
// they must not ship their own copies. Shadow rewrites the references in each project's own classes (string constants
// included), so applying this one rule on both sides keeps the names in step.
//
// A module built outside this repository must apply the same relocation, or it will fail to load
// with NoClassDefFoundError.
tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    val base = "io.github.md5sha256.realty.libraries"
    relocate("com.minecraftcitiesnetwork.pluginInfrastructure", "${base}.com.minecraftcitiesnetwork.pluginInfrastructure")
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
}
