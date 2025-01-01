plugins {
    // todo: bytte ut plugin med å bare bruke java og xjc bindings
    //  https://eclipse-ee4j.github.io/jaxb-ri/4.0.5/docs/ch01.html#jaxb-2-0-sample-apps
    id("com.github.bjornvester.xjc") version "1.8.2"
}

dependencies {
    api("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.18.2")
    api("jakarta.xml.bind:jakarta.xml.bind-api:4.0.2")
    runtimeOnly("com.sun.xml.bind:jaxb-impl:4.0.5")

    testImplementation(kotlin("test"))
    testImplementation("org.assertj:assertj-core:3.27.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
}

xjc {
    xsdDir.set(layout.projectDirectory.dir("main/schema/xml"))
}
