dependencies {
    api(project(":libs:utils"))
    api(project(":libs:xml"))

    api("com.ibm.mq:com.ibm.mq.allclient:9.4.1.0")

    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    testImplementation(kotlin("test"))
    testImplementation("org.testcontainers:testcontainers:1.20.3")
}
