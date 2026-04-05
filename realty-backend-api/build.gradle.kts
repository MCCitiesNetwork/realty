plugins {
    `realty-conventions`
    `java-library`
    `maven-publish`
}

dependencies {
    compileOnly("org.jetbrains:annotations:26.0.2-1")
}

java {
    withSourcesJar()
}

publishing {
    repositories {
        maven {
            name = "deploy"
            url = uri(providers.gradleProperty("deployUrl").getOrElse("https://example.com"))
            credentials {
                username = providers.gradleProperty("deployUsername").orNull
                password = providers.gradleProperty("deployPassword").orNull
            }
        }
    }
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("Realty Backend API")
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
