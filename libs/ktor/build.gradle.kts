val ktorVersion = "3.0.3"

dependencies {
    api(project(":libs:utils"))

    implementation("io.ktor:ktor-server-core:$ktorVersion")
}
