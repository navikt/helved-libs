dependencies {
    implementation(project(":libs:postgres"))
    implementation(project(":libs:utils"))
    implementation(kotlin("test"))
    api("org.testcontainers:postgresql:1.19.8")
}
