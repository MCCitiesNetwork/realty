plugins {
    `realty-conventions`
    `java-library`
    `maven-publish`
}

dependencies {
    api(project(":realty-backend-api"))
    compileOnly("org.jetbrains:annotations:26.0.2-1")
    api("org.spongepowered:configurate-yaml:4.2.0")
    api("org.mybatis:mybatis:3.5.19")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.6")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.testcontainers:testcontainers-mariadb:2.0.1")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.2")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("Realty Common")
                description.set("Core database and domain logic for Realty")
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
