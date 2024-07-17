dependencies {
    implementation(project(":libs:jdbc"))
    implementation(project(":libs:utils"))
    implementation(kotlin("test"))
    api("org.testcontainers:postgresql:1.20.0")
}
