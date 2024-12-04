val ktorVersion = "3.0.0"

dependencies {
    implementation(project(":libs:utils"))

    implementation("io.micrometer:micrometer-registry-prometheus:1.14.1")
    implementation("org.apache.kafka:kafka-streams:3.9.0")

    

    implementation("ch.qos.logback:logback-classic:1.5.3")
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.0")



    testImplementation(kotlin("test"))
    testImplementation("org.apache.kafka:kafka-streams-test-utils:3.9.0")
}
