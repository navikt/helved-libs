package libs.kafka

import kotlin.test.assertEquals
import libs.kafka.*
import no.nav.aap.kafka.streams.v2.*
import org.junit.jupiter.api.Test

class KTableTest {

    @Test
    fun `can make it to stream`() {
        val kafka = Mock.withTopology {
            consume(Tables.B)
                .toStream()
                .produce(Topics.C)
        }

        kafka.inputTopic(Topics.B)
            .produce("1", "hello")

        val result = kafka.outputTopic(Topics.C).readKeyValuesToMap()
        assertEquals(1, result.size)
        assertEquals("hello", result["1"])
    }

    @Test
    fun `join filtered topic with table and make it to stream`() {
        val kafka = Mock.withTopology {
            consume(Tables.B)
                .toStream()
                .filter { it != "humbug" }
                .joinWith(consume(Tables.C))
                .map { a, b -> b + a }
                .produce(Topics.D)
        }

        kafka.inputTopic(Topics.C)
            .produce("1", "awesome")
            .produce("2", "nice")

        kafka.inputTopic(Topics.B)
            .produce("1", "sauce")
            .produce("1", "humbug")
            .produce("2", "humbug")
            .produce("2", "price")

        val result = kafka.outputTopic(Topics.D).readKeyValuesToMap()
        assertEquals(2, result.size)
        assertEquals("awesomesauce", result["1"])
        assertEquals("niceprice", result["2"])
    }
}

