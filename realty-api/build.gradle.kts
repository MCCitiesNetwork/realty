plugins {
    `realty-conventions`
    `java-library`
    `maven-publish`
}

dependencies {
    compileOnly("org.jetbrains:annotations:26.0.2-1")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("Realty API")
                description.set("API interfaces and domain types for Realty")
                url.set("https://github.com/MCCitiesNetwork/realty")
                developers {
                    developer {
                        id.set("md5sha256")
                        name.set("Andrew Wong")
                        email.set("42793301+md5sha256@users.noreply.github.com")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/MCCitiesNetwork/realty.git")
                    developerConnection.set("scm:git:ssh://github.com/MCCitiesNetwork/realty.git")
                    url.set("https://github.com/MCCitiesNetwork/realty")
                }
            }
        }
    }
}
