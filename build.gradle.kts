plugins {
    kotlin("jvm") version "1.9.23"
    `maven-publish`
    `java-library`
}

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    group = "navikt.helved.libs"

    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "maven-publish")
    apply(plugin = "java-library")

    tasks {
        withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
            kotlinOptions.jvmTarget = "21"
        }
        withType<Jar> {
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }
        withType<Test> {
            useJUnitPlatform()
        }
    }

    java {
        withSourcesJar()
        withJavadocJar()
    }

    publishing {
        publications {
            create<MavenPublication>("mavenJava") {
                artifactId = project.name
                version = project.findProperty("version")?.toString() ?: "0.0.1"
                from(components["java"])
            }
        }

        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/navikt/helved-libs")
                credentials {
                    username = "x-access-token"
                    password = System.getenv("GITHUB_TOKEN")
                }
            } 
        }
    }

    kotlin.sourceSets["main"].kotlin.srcDirs("main")
    kotlin.sourceSets["test"].kotlin.srcDirs("test")
    sourceSets["main"].resources.srcDirs("main")
    sourceSets["test"].resources.srcDirs("test")
}