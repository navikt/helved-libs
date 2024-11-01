val ktorVersion = "3.0.0"

dependencies {
    implementation(project(":libs:utils"))

    api("org.apache.kafka:kafka-clients:3.8.1")

    testImplementation(kotlin("test"))
}
