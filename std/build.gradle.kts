val ktorVersion = "3.0.1"

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.9.0")
    testImplementation(kotlin("test"))
    testImplementation(project(":test"))

    // JDBC
    api("org.postgresql:postgresql:42.7.4")
    api("com.zaxxer:HikariCP:6.1.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("com.h2database:h2:2.3.232")

    // UTILS
    api("ch.qos.logback:logback-classic:1.5.12")
    runtimeOnly("net.logstash.logback:logstash-logback-encoder:8.0")

    // KTOR
    api("io.ktor:ktor-client-cio:$ktorVersion")
    api("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    api("io.ktor:ktor-client-logging:$ktorVersion")
    api("io.ktor:ktor-server-core:$ktorVersion")
    api("io.ktor:ktor-serialization-jackson:$ktorVersion")
    api("com.fasterxml.jackson.core:jackson-databind:2.18.1")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.1")

    // KAFKA
    api("org.apache.kafka:kafka-clients:3.9.0")

    // AUTH
    api("io.ktor:ktor-client-auth:$ktorVersion")
    api("io.ktor:ktor-server-auth:$ktorVersion")
    api("io.ktor:ktor-server-auth-jwt:$ktorVersion")
}

sourceSets {
    main {
        kotlin.srcDir("src")
        resources.srcDir("src")
    }
    test {
        kotlin.srcDir("test",)
        resources.srcDir("test")
    }
}
