import org.gradle.kotlin.dsl.application

plugins {
    kotlin("jvm") version "2.2.20"
    id("com.google.devtools.ksp") version "2.2.20-2.0.4"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "org.lonwulf.labs"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.14")
    implementation("com.squareup.moshi:moshi:1.15.2")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.2")
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.2")

    implementation("org.apache.commons:commons-csv:1.10.0")
    implementation("org.xerial:sqlite-jdbc:3.42.0.0")
    implementation("org.slf4j:slf4j-simple:2.0.13")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
    implementation("io.insert-koin:koin-core:4.1.0")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("org.lonwulf.labs.jobcrawler.MainKt")
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    archiveClassifier.set("") // Ensures the output is `app.jar`, not `app-all.jar`
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
    mergeServiceFiles()
}
tasks {
    // Ensure the Shadow JAR is built before any packaging or start script tasks
    named("distZip") {
        dependsOn("shadowJar")
    }
    named("distTar") {
        dependsOn("shadowJar")
    }
    named("startScripts") {
        dependsOn("shadowJar")
        dependsOn("jar")
    }
    named("startShadowScripts") {
        dependsOn("shadowJar")
        dependsOn("jar")
    }

    build {
        dependsOn(shadowJar)
    }

    register("stage") {
        dependsOn("shadowJar")
    }

    withType<JavaExec> {
        standardInput = System.`in`
    }
}

kotlin {
    jvmToolchain(17)
}
