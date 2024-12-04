package libs.kafka

import org.apache.kafka.streams.TestInputTopic
import org.apache.kafka.streams.TestOutputTopic

data class TestTopic<V: Any>(
    private val input: TestInputTopic<String, V>,
    private val output: TestOutputTopic<String, V>
) {
    fun produce(key: String, value: () -> V) = this.also {
        input.pipeInput(key, value())
    }

    fun tombstone(key: String) = input.pipeInput(key, null)

    fun assertThat() = output.readAndAssert()

    fun readValue(): V = output.readValue()
}
