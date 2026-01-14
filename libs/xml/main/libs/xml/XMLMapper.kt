package libs.xml

import jakarta.xml.bind.JAXBContext
import jakarta.xml.bind.Marshaller
import jakarta.xml.bind.Marshaller.JAXB_FORMATTED_OUTPUT
import java.io.StringReader
import java.io.StringWriter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.xml.stream.XMLInputFactory
import javax.xml.transform.stream.StreamSource
import kotlin.reflect.KClass

class XMLMapper<T : Any>(
    private val type: KClass<T>,
    private val enableEncodingDeclaration: Boolean = true, // <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
) {
    private val context get() = JAXBContext.newInstance(type.java)
    private val marshaller get() = context.createMarshaller().apply {
        setProperty(JAXB_FORMATTED_OUTPUT, true)
        if (!enableEncodingDeclaration) setProperty(Marshaller.JAXB_FRAGMENT, true)
    }
    private val unmarshaller get() = context.createUnmarshaller()
    private val inputFactory get() = XMLInputFactory.newInstance()

    companion object {
        inline operator fun <reified T : Any> invoke(enableEncodingDeclaration: Boolean = true): XMLMapper<T> {
            return XMLMapper(T::class, enableEncodingDeclaration)
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

    fun readValue(value: ByteArray?): T? {
        if (value == null) return null
        val reader = inputFactory.createXMLStreamReader(StreamSource(ByteArrayInputStream(value)))
        val jaxb = unmarshaller.unmarshal(reader, type.java)
        reader.close()
        return jaxb.value
    }

    fun copy(value: T): T {
        val outStream = ByteArrayOutputStream()
        marshaller.marshal(value, outStream)
        val reader = inputFactory.createXMLStreamReader(StreamSource(ByteArrayInputStream(outStream.toByteArray())))
        val copy = unmarshaller.unmarshal(reader, type.java)
        reader.close()
        return copy.value
    }
}
