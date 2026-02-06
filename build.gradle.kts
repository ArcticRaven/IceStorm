plugins {
    id("java-library")
    id("maven-publish")
}

group = "dev.arctic"
version = "0.2.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
    maven("https://maven.hytale.com/release")
}

dependencies {
    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")

    compileOnly("com.hypixel.hytale:Server:2026.01.28-87d03be09")
    implementation("com.zaxxer:HikariCP:7.0.2")

    implementation("org.xerial.sqlite-jdbc:3.51.1.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.4.1")

    api("com.google.code.gson:gson:2.13.2")
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name.set(project.name)
                description.set("Shared library for Arctic's Hytale projects.")
            }
        }
    }
}

tasks.named("build") {
    dependsOn("publishToMavenLocal")
}
