val ktorVersion = "3.0.0"

dependencies {
    implementation(project(":libs:utils"))
    implementation(project(":libs:jdbc"))

    testImplementation(kotlin("test"))
    testImplementation("com.h2database:h2:2.3.232")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
