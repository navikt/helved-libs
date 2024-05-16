val ktorVersion = "2.3.9"

dependencies {
    api("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.17.1")
    api("javax.xml.bind:jaxb-api:2.3.1")
    api("org.glassfish.jaxb:jaxb-runtime:4.0.5")

    testImplementation(kotlin("test"))
    testImplementation("org.glassfish.jaxb:jaxb-runtime:4.0.5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
