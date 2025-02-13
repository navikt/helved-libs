package libs.kafka

import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.TestOutputTopic
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

fun <V : Any> TestOutputTopic<String, V>.readAndAssert() = TopicAssertion.readAndAssertThat(this)

class TopicAssertion<V : Any> private constructor(topic: TestOutputTopic<String, V>) {
    companion object {
        fun <V : Any> readAndAssertThat(topic: TestOutputTopic<String, V>) = TopicAssertion(topic)
    }

    private val actuals: List<KeyValue<String, V>> = topic.readKeyValuesToList()
    private fun valuesForKey(key: String) = actuals.filter { it.key == key }.map { it.value }

    fun hasValueEquals(key: String, index: Int = 0, value: () -> V) = this.also {
        val values = valuesForKey(key)
        assertTrue("No values found for key: $key") { values.isNotEmpty() }
        val actual = values.getOrNull(index) ?: fail("No value for key $key on index $index/${values.size - 1} found.")
        assertEquals(value(), actual)
    }

    fun hasNumberOfRecords(amount: Int) = this.also {
        assertEquals(amount, actuals.size)
    }

    fun hasNumberOfRecordsForKey(key: String, amount: Int) = this.also {
        assertEquals(amount, actuals.filter { it.key == key }.size)
    }

    fun hasKey(key: String) = this.also {
        assertEquals(key, actuals.firstOrNull { it.key == key }?.key)
    }

    fun hasValue(value: V) = this.also {
        assertEquals(value, actuals.firstOrNull()?.value)
    }

    fun isEmpty() = this.also {
        assertTrue(actuals.isEmpty())
    }

    fun isEmptyForKey(key: String) = this.also {
        assertFalse(actuals.any { it.key == key })
    }

    fun hasValueMatching(key: String, index: Int = 0, assertions: (value: V) -> Unit) = this.also {
        val values = valuesForKey(key)
        assertTrue("No values found for key: $key") { values.isNotEmpty() }
        val value = values.getOrNull(index) ?: fail("No value for key $key on index $index/${values.size - 1} found.")
        assertions(value)
    }

    fun withLastValue(assertions: (value: V?) -> Unit) = this.also {
        val value = actuals.last().value ?: fail("No records found.")
        assertions(value)
    }

    fun hasValuesForPredicate(key: String, numberOfValues: Int = 1, predicate: (value: V) -> Boolean) = this.also {
        val values = valuesForKey(key).filter(predicate)
        assertEquals(numberOfValues, values.size, "Should only be $numberOfValues values matching predicate")
    }

    fun containsTombstone(key: String) = this.also {
        assertTrue(valuesForKey(key).contains(null))
    }
}
