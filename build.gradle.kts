plugins {
    kotlin("jvm") version "2.0.10"
    `maven-publish`
    `java-library`
}

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    group = "no.nav.helved" 

    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "maven-publish")
    apply(plugin = "java-library")

    tasks {
        withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
            compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
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

    sourceSets {
        main {
            kotlin.srcDir("main")
            resources.srcDir("main")
        }
        test {
            kotlin.srcDir("test")
            resources.srcDir("test")
        }
    }
}
