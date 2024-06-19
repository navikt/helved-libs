dependencies {
    implementation(project(":libs:postgres"))
    implementation(project(":libs:utils"))
    api("org.testcontainers:postgresql:1.19.8")
}
