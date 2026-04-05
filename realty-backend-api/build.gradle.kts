plugins {
    `realty-conventions`
    `realty-publish`
}

dependencies {
    compileOnlyApi("org.jetbrains:annotations:26.0.2-1")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("Realty Backend API")
                description.set("API interfaces and domain types for Realty")
            }
        }
    }
}
