import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("java-library")
    id("maven-publish")
    id("com.gradleup.shadow") version "9.3.1"
}

group = "dev.arctic"
version = "0.2.1"

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
    implementation("org.xerial:sqlite-jdbc:3.51.1.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.4.1")
    implementation("org.apache.pulsar:pulsar-client:4.1.2")
    implementation("com.google.code.gson:gson:2.13.2")
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}

tasks.named<ShadowJar>("shadowJar") {
    // Shade everything in runtimeClasspath (i.e., implementation + runtimeOnly), excluding compileOnly.
    configurations = listOf(project.configurations.runtimeClasspath.get())

    // Single output jar (no "-all" classifier)
    archiveClassifier.set("")

    // Important for JDBC drivers + other SPI-based libs
    mergeServiceFiles()
}

tasks.named<Jar>("jar") {
    enabled = false
}

tasks.named("assemble") {
    dependsOn("shadowJar")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            // Publish the shaded jar as the main artifact
            artifact(tasks.named("shadowJar"))

            // Keep sources/javadoc jars
            artifact(tasks.named("sourcesJar"))
            artifact(tasks.named("javadocJar"))

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
