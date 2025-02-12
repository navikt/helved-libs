val ktorVersion = "3.0.2"

dependencies {
    implementation(project(":libs:utils"))
    implementation(project(":libs:xml"))

    implementation("io.micrometer:micrometer-registry-prometheus:1.14.4")
    implementation("org.apache.kafka:kafka-streams:3.9.0")

    implementation("ch.qos.logback:logback-classic:1.5.16")
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2")

    testImplementation(kotlin("test"))
    testImplementation("org.apache.kafka:kafka-streams-test-utils:3.9.0")
}
