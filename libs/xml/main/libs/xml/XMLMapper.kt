package libs.xml

import jakarta.xml.bind.JAXBContext
import jakarta.xml.bind.Marshaller.JAXB_FORMATTED_OUTPUT
import java.io.StringReader
import java.io.StringWriter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.xml.stream.XMLInputFactory
import javax.xml.transform.stream.StreamSource
import kotlin.reflect.KClass

class XMLMapper<T : Any>(private val type: KClass<T>) {
    private val context get() = JAXBContext.newInstance(type.java)
    private val marshaller get() = context.createMarshaller().apply { setProperty(JAXB_FORMATTED_OUTPUT, true) }
    private val unmarshaller get() = context.createUnmarshaller()
    private val inputFactory get() = XMLInputFactory.newInstance()

    companion object {
        inline operator fun <reified T : Any> invoke(): XMLMapper<T> {
            return XMLMapper(T::class)
        }
    }

    fun readValue(value: String): T {
        val jaxb = StringReader(value).use { sr ->
            val reader = inputFactory.createXMLStreamReader(StreamSource(sr))
            val jaxb = unmarshaller.unmarshal(reader, type.java)
            reader.close()
            jaxb
        }

        return jaxb.value
    }

    fun writeValueAsString(value: T): String {
        val stringWriter = StringWriter()
        marshaller.marshal(value, stringWriter)
        return stringWriter.toString()
    }

    fun writeValueAsBytes(value: T): ByteArray {
        val outStream = ByteArrayOutputStream()
        marshaller.marshal(value, outStream)
        return outStream.toByteArray()
    }

    fun readValue(value: ByteArray): T {
        val reader = inputFactory.createXMLStreamReader(StreamSource(ByteArrayInputStream(value)))
        val jaxb = unmarshaller.unmarshal(reader, type.java)
        reader.close()
        return jaxb.value
    }
}
