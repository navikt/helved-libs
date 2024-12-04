package libs.kafka.stream

import libs.kafka.*
import libs.kafka.StreamsMock
import libs.kafka.Tables
import libs.kafka.Topics
import libs.kafka.produce
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class MappedStreamTest {
    @Test
    fun `map a filtered joined stream`() {
        val kafka = StreamsMock.withTopology {
            val table = consume(Tables.B)
            consume(Topics.A)
                .joinWith(table)
                .filter { (a, _) -> a == "sauce" }
                .map { a, b -> b + a }
                .produce(Topics.C)
        }


        kafka.inputTopic(Topics.B)
            .produce("1", "awesome")
            .produce("2", "nice")

        kafka.inputTopic(Topics.A)
            .produce("1", "sauce")
            .produce("2", "price")

        val result = kafka.outputTopic(Topics.C).readKeyValuesToMap()

        assertEquals(1, result.size)
        assertEquals("awesomesauce", result["1"])
        assertNull(result["2"])
    }

    @Test
    fun `map a filtered left joined stream`() {
        val kafka = StreamsMock.withTopology {
            val table = consume(Tables.B)
            consume(Topics.A)
                .leftJoinWith(table)
                .filter { (a, _) -> a == "sauce" }
                .map { a, b -> b + a }
                .produce(Topics.C)
        }

        kafka.inputTopic(Topics.B)
            .produce("1", "awesome")
            .produce("2", "nice")

        kafka.inputTopic(Topics.A)
            .produce("1", "sauce")
            .produce("2", "price")

        val result = kafka.outputTopic(Topics.C).readKeyValuesToMap()

        assertEquals(1, result.size)
        assertEquals("awesomesauce", result["1"])
        assertNull(result["2"])
    }

    @Test
    fun `map a joined stream`() {
        val kafka = StreamsMock.withTopology {
            val table = consume(Tables.B)
            consume(Topics.A)
                .joinWith(table)
                .map { a, b -> b + a }
                .produce(Topics.C)
        }

        kafka.inputTopic(Topics.B)
            .produce("1", "awesome")
            .produce("2", "nice")

        kafka.inputTopic(Topics.A)
            .produce("1", "sauce")
            .produce("2", "price")

        val result = kafka.outputTopic(Topics.C).readKeyValuesToMap()

        assertEquals(2, result.size)
        assertEquals("awesomesauce", result["1"])
        assertEquals("niceprice", result["2"])
    }

    @Test
    fun `mapNotNull a branched stream`() {
        val kafka = StreamsMock.withTopology {
            consume(Topics.A)
                .mapNotNull { key, value -> if (key == "1") null else value }
                .produce(Topics.C)
        }

        kafka.inputTopic(Topics.A)
            .produce("1", "sauce")
            .produce("2", "price")

        val result = kafka.outputTopic(Topics.C).readKeyValuesToMap()

        assertEquals(1, result.size)

        assertEquals("price", result["2"])
    }

    @Test
    fun `map a left joined stream`() {
        val kafka = StreamsMock.withTopology {
            val table = consume(Tables.B)
            consume(Topics.A)
                .leftJoinWith(table)
                .map { a, b -> b + a }
                .produce(Topics.C)
        }

        kafka.inputTopic(Topics.B)
            .produce("1", "awesome")
            .produce("2", "nice")

        kafka.inputTopic(Topics.A)
            .produce("1", "sauce")
            .produce("2", "price")

        val result = kafka.outputTopic(Topics.C).readKeyValuesToMap()

        assertEquals(2, result.size)
        assertEquals("awesomesauce", result["1"])
        assertEquals("niceprice", result["2"])
    }

    @Test
    fun `map key and value`() {
        val kafka = StreamsMock.withTopology {
            val table = consume(Tables.B)
            consume(Topics.A)
                .leftJoinWith(table)
                .mapKeyValue { key, left, right -> KeyValue("$key$key", right + left) }
                .produce(Topics.C)
        }

        kafka.inputTopic(Topics.B)
            .produce("1", "awesome")
            .produce("2", "nice")

        kafka.inputTopic(Topics.A)
            .produce("1", "sauce")
            .produce("2", "price")

        val result = kafka.outputTopic(Topics.C).readKeyValuesToMap()

        assertEquals(2, result.size)
        assertEquals("awesomesauce", result["11"])
        assertEquals("niceprice", result["22"])
    }

    @Test
    fun `map and use custom processor`() {
        val kafka = StreamsMock.withTopology {
            consume(Topics.A)
                .map { v -> v }
                .processor(CustomProcessor())
                .produce(Topics.C)
        }

        kafka.inputTopic(Topics.A).produce("1", "a")

        val result = kafka.outputTopic(Topics.C).readKeyValuesToMap()

        assertEquals(1, result.size)
        assertEquals("a.v2", result["1"])
    }

    @Test
    fun `map and use custom processor with table`() {
        val kafka = StreamsMock.withTopology {
            val table = consume(Tables.B)
            consume(Topics.A)
                .map { v -> v }
                .processor(CustomProcessorWithTable(table))
                .produce(Topics.C)
        }

        kafka.inputTopic(Topics.B).produce("1", ".v2")
        kafka.inputTopic(Topics.A).produce("1", "a")

        val result = kafka.outputTopic(Topics.C).readKeyValuesToMap()

        assertEquals(1, result.size)
        assertEquals("a.v2", result["1"])
    }

    @Test
    fun `rekey a mapped stream`() {
        val kafka = StreamsMock.withTopology {
            consume(Topics.A)
                .map { v -> "${v}alue" }
                .rekey { v -> v }
                .produce(Topics.C)
        }

        kafka.inputTopic(Topics.A).produce("k", "v")

        val result = kafka.outputTopic(Topics.C).readKeyValuesToMap()

        assertEquals(1, result.size)
        assertEquals("value", result["value"])
    }

    @Test
    fun `filter a filtered mapped stream`() {
        val kafka = StreamsMock.withTopology {
            consume(Topics.A)
                .filter { it.contains("nice") }
                .filter { it.contains("price") }
                .produce(Topics.C)
        }

        kafka.inputTopic(Topics.A)
            .produce("1", "awesomenice")
            .produce("2", "niceprice")

        val result = kafka.outputTopic(Topics.C).readKeyValuesToMap()

        assertEquals(1, result.size)
        assertEquals("niceprice", result["2"])
    }

    @Test
    fun `rekey with mapKeyValue`() {
        val kafka = StreamsMock.withTopology {
            consume(Topics.A)
                .mapKeyAndValue { key, value -> KeyValue(key = "test:$key", value = "$value$value") }
                .produce(Topics.C)
        }

        kafka.inputTopic(Topics.A)
            .produce("1", "a")
            .produce("2", "b")

        val result = kafka.outputTopic(Topics.C).readKeyValuesToMap()

        assertEquals(2, result.size)
        assertEquals("aa", result["test:1"])
        assertEquals("bb", result["test:2"])
    }
}
