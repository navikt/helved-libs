dependencies {
    implementation(project(":libs:utils"))
    implementation(project(":libs:kafka"))

    implementation("io.micrometer:micrometer-registry-prometheus:1.14.5")
    implementation("org.apache.kafka:kafka-streams:4.0.0")
    implementation("org.apache.kafka:kafka-streams-test-utils:4.0.0")
    implementation(kotlin("test"))
}
