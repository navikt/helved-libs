dependencies {
    api("ch.qos.logback:logback-classic:1.5.15")
    runtimeOnly("net.logstash.logback:logstash-logback-encoder:8.0")
    testImplementation(kotlin("test"))
}
