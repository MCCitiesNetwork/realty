plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // Puts Shadow on every build script's classpath so realty-shared-relocation can configure
    // shadowJar. Projects therefore apply com.gradleup.shadow without a version -- Gradle rejects a
    // versioned request for a plugin that is already on the classpath.
    implementation("com.gradleup.shadow:shadow-gradle-plugin:9.3.1")
}
