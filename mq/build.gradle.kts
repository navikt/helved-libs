val ktorVersion = "3.0.1"

plugins {
    // todo: bytte ut plugin med å bare bruke java og xjc bindings
    //  https://eclipse-ee4j.github.io/jaxb-ri/4.0.5/docs/ch01.html#jaxb-2-0-sample-apps
    id("com.github.bjornvester.xjc") version "1.8.2"
}

dependencies {
    api(project(":std"))
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    testImplementation(kotlin("test"))
    testImplementation(project(":test"))

    // MQ
    api("com.ibm.mq:com.ibm.mq.allclient:9.4.1.0")
    testImplementation("org.testcontainers:testcontainers:1.20.3")

    // WS
    testImplementation("io.ktor:ktor-server-core:$ktorVersion")
    testImplementation("io.ktor:ktor-server-netty:$ktorVersion")
    testImplementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    testImplementation("io.ktor:ktor-client-cio:$ktorVersion")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")

    // XML
    api("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.18.1")
    api("jakarta.xml.bind:jakarta.xml.bind-api:4.0.2")
    runtimeOnly("com.sun.xml.bind:jaxb-impl:4.0.5")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

}

// https://github.com/navikt/tjenestespesifikasjoner
// https://stackoverflow.com/questions/48204141/replacements-for-deprecated-jpms-modules-with-java-ee-apis/48204154#48204154
xjc {
    xsdDir.set(layout.projectDirectory.dir("src/xml/schemas"))
}

sourceSets {
    main {
        kotlin.srcDir("src")
        resources.srcDir("src")
    }
    test {
        kotlin.srcDir("test")
        resources.srcDir("test")
    }
}
