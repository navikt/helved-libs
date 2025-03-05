dependencies {
    implementation(project(":libs:mq"))
    implementation(project(":libs:utils"))
    api("org.testcontainers:testcontainers:1.20.5")
    api("com.ibm.mq:com.ibm.mq.allclient:9.4.1.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
}