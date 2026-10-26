plugins {
    `java-library`
    `realty-conventions`
    id("com.gradleup.shadow")
    `realty-shared-relocation`
}

dependencies {
    compileOnly(project(":realty-paper"))
    compileOnly(project(":realty-paper-api"))
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.74-stable")
    compileOnly("org.jetbrains:annotations:26.0.2-1")
    compileOnly("com.minecraftcitiesnetwork:plugin-infrastructure:1.0.0-SNAPSHOT")

    testImplementation(project(":realty-paper-api"))
    testImplementation("io.papermc.paper:paper-api:26.1.2.build.74-stable")
}
