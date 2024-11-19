package test

import org.testcontainers.containers.GenericContainer
import mq.Config

class LocalMQContainerForM3(appname: String) : AutoCloseable {
    private val container: GenericContainer<Nothing> =
        GenericContainer<Nothing>("ibm-mqadvanced-server-dev:9.4.0.0-arm64").apply {
            withLabel("service", appname)
            withReuse(true)
            withNetwork(null)
            withEnv("MQ_ADMIN_PASSWORD", "passw0rd")
            withCreateContainerCmdModifier { cmd ->
                cmd.withName("$appname-mq")
                cmd.hostConfig?.apply {
                    withMemory(512 * 1024 * 1024)
                    withMemorySwap(2 * 512 * 1024 * 1024)
                }
            }
            withEnv("LICENSE", "accept")
            withEnv("MQ_QMGR_NAME", "QM1")
            withExposedPorts(1414)
            start()
        }

    val config by lazy {
        Config(
            host = "127.0.0.1",
            port = container.getMappedPort(1414),
            channel = "DEV.ADMIN.SVRCONN",
            manager = "QM1",
            username = "admin",
            password = "passw0rd",
        )
    }

    override fun close() {}
}
