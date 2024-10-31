val ktorVersion = "3.0.1"

dependencies {
    implementation(project(":libs:utils"))

    api("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    api("io.ktor:ktor-client-cio:$ktorVersion")
    api("io.ktor:ktor-serialization-jackson:$ktorVersion")
    api("com.fasterxml.jackson.core:jackson-databind:2.18.1")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.2")
    api("io.ktor:ktor-client-logging:$ktorVersion")

    testImplementation(kotlin("test"))
}
