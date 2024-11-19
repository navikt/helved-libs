dependencies {
    implementation(project(":std"))
    implementation(project(":mq"))
    api("com.nimbusds:nimbus-jose-jwt:9.47")
    api("org.testcontainers:postgresql:1.20.3")
    api("com.h2database:h2:2.3.232")
}

sourceSets {
    main {
        kotlin.srcDir("src")
        resources.srcDir("src")
    }
    test {
        kotlin.srcDir("test")
        resources.srcDir("test")
    }
}
