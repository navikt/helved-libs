dependencies {
    api(project(":libs:utils"))
    api(project(":libs:tracing"))
    api(project(":libs:xml"))

    api("com.ibm.mq:com.ibm.mq.allclient:9.4.1.0")

    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    testImplementation(kotlin("test"))
    testImplementation(project(":libs:mq-test"))
}
