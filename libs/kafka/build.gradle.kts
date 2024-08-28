val ktorVersion = "2.3.9"

dependencies {
    implementation(project(":libs:utils"))

    api("org.apache.kafka:kafka-clients:3.8.0")

    testImplementation(kotlin("test"))
}
