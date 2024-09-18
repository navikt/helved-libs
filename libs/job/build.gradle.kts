val ktorVersion = "2.3.9"

dependencies {
    implementation(project(":libs:utils"))

    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    testImplementation(kotlin("test"))
}
