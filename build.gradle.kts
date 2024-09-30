plugins {
    kotlin("jvm") version "2.0.20"
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

private val deps = mutableSetOf<File>()
subprojects {
    task("deps") {
        deps += (fileTree("${gradle.gradleHomeDir}/lib") +
                fileTree("${gradle.gradleUserHomeDir}/caches/${gradle.gradleVersion}/generated-gradle-jars") +
                fileTree("${gradle.gradleUserHomeDir}/caches/modules-2/files-2.1"))
            .filter { it.extension == "jar" }
            .files

        deps += (project.properties["gradleKotlinDsl.accessorsClassPath"] as? org.gradle.kotlin.dsl.accessors.AccessorsClassPath)
            ?.bin
            ?.asFiles
            ?: emptyList()

        deps.filter { it.extension == "jar" || it.isDirectory }
            .map { it.absolutePath }
            .toSet()
            .joinToString(":")
            .let { classpath ->
                File(project.rootDir, "kls-classpath").apply {
                    delete()
                    appendText("#/bin/bash\n")
                    appendText("echo ")
                    appendText(classpath)
                    setExecutable(true)
                }
            }
    }
}
