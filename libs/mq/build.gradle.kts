dependencies {
    api(project(":libs:utils"))
    api(project(":libs:xml"))

    api("com.ibm.mq:com.ibm.mq.allclient:9.3.5.1")

    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")


    testImplementation(kotlin("test"))
    testImplementation("org.testcontainers:testcontainers:1.19.7")
}
