dependencies {
    implementation(project(":libs:utils"))

    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    api("org.postgresql:postgresql:42.7.4")
    api("com.zaxxer:HikariCP:6.0.0")
    api("org.flywaydb:flyway-database-postgresql:10.18.1")

    testImplementation("com.h2database:h2:2.3.232")
    testImplementation(kotlin("test"))
}
