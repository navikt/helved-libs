plugins {
    id("com.github.bjornvester.xjc") version "1.8.2"
}

dependencies {
    api("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.17.1")
    api("jakarta.xml.bind:jakarta.xml.bind-api:4.0.2")
    runtimeOnly("com.sun.xml.bind:jaxb-impl:4.0.5")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

xjc {
    xsdDir.set(layout.projectDirectory.dir("main/schema"))
}
