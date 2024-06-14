val ktorVersion = "2.3.11"

dependencies {
    api(project(":libs:utils"))
    api(project(":libs:job"))
    api(project(":libs:postgres"))

    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    testImplementation(kotlin("test"))
    testImplementation("com.h2database:h2:2.2.224")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
