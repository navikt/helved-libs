val ktorVersion = "2.3.9"

dependencies {
    implementation(project(":libs:utils"))

    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    testImplementation(kotlin("test"))
}
