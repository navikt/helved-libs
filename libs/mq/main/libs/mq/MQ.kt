package libs.mq

import com.ibm.mq.constants.CMQC
import com.ibm.mq.jms.MQConnectionFactory
import com.ibm.mq.jms.MQQueue
import com.ibm.msg.client.jms.JmsConstants
import com.ibm.msg.client.wmq.WMQConstants
import libs.tracing.*
import libs.utils.logger
import libs.utils.secureLog
import java.util.*
import java.time.LocalDateTime
import javax.jms.JMSContext
import javax.jms.JMSProducer
import javax.jms.MessageListener
import javax.jms.TextMessage
import javax.jms.ExceptionListener
import javax.jms.JMSException
import javax.jms.JMSConsumer

private val mqLog = logger("mq")

private val traceparents = mutableMapOf<String, String>()

interface MQProducer {
    fun produce(message: String, config: JMSProducer.() -> Unit = {})
}
class DefaultMQProducer(
    private val mq: MQ,
    private val queue: MQQueue,
): MQProducer {
    override fun produce(
        message: String,
        config: JMSProducer.() -> Unit,
    ) {
        mqLog.info("Producing message on ${queue.baseQueueName}")
        return mq.transaction { ctx ->
            ctx.clientID = UUID.randomUUID().toString()
            val producer = ctx.createProducer().apply(config)
            val message = ctx.createTextMessage(message)
            producer.send(queue, message)

            getTraceparent()?.let { traceparent ->
                traceparents[message.jmsMessageID] = traceparent
            }
        }
    }
}

internal interface MQListener {
    fun onMessage(message: TextMessage)
}

abstract class MQConsumer(
    private val mq: MQ,
    private val queue: MQQueue,
) : AutoCloseable, MQListener, ExceptionListener {
    private var context = createContext()
    private var consumer = createConsumer()

    private fun createContext(): JMSContext {
        return mq.context.apply {
            autoStart = false
            exceptionListener = this@MQConsumer
        }
    }

    private fun createConsumer(): JMSConsumer {
        return context.createConsumer(queue).apply {
            messageListener = MessageListener {
                mqLog.info("Consuming message on ${queue.baseQueueName}")
                mq.transacted(context) {
                    val span = traceparents.get(it.jmsCorrelationID)?.let { traceparent ->
                        tracer.spanBuilder(queue.baseQueueName)
                            .setParent(propagateSpan(traceparent))
                            .startSpan()
                    }
                    try {
                        onMessage(it as TextMessage)
                    } finally {
                        span?.end()
                    }
                }
            }
        }
    }

    override fun onException(exception: JMSException) {
        mqLog.error("Connection exception occured. Reconnecting ${queue.baseQueueName}...", exception)
        reconnect()
    }

    private var lastReconnect = LocalDateTime.now()

    @Synchronized
    fun reconnect() {
        if (lastReconnect.plusMinutes(1).isAfter(LocalDateTime.now())) return
        try {
            runCatching { 
                close() // we dont care if this fails (maybe already closed)
            }
            context = createContext() 
            consumer = createConsumer()
            start()
            mqLog.info("Sucessfully reconnected consumer ${queue.baseQueueName}")
        } catch(e: Exception) {
            mqLog.error("Failed to reconnect", e)
        } finally {
            lastReconnect = LocalDateTime.now()
        }
    }

    fun depth(): Int {
        return mq.depth(queue)
    }

    fun start() {
        context.start()
    }

    override fun close() {
        consumer.close()
        context.close()
    }
}

class MQ(private val config: MQConfig) {
    private val factory: MQConnectionFactory = MQConnectionFactory().apply {
        hostName = config.host
        port = config.port
        queueManager = config.manager
        channel = config.channel
        transportType = WMQConstants.WMQ_CM_CLIENT
        ccsid = JmsConstants.CCSID_UTF8
        userAuthenticationMQCSP = true
        setIntProperty(JmsConstants.JMS_IBM_ENCODING, CMQC.MQENC_NATIVE)
        setIntProperty(JmsConstants.JMS_IBM_CHARACTER_SET, JmsConstants.CCSID_UTF8)
        // clientReconnectOptions = WMQConstants.WMQ_CLIENT_RECONNECT_Q_MGR // try to reconnect to the same queuemanager
        // clientReconnectTimeout = 600 // reconnection attempts for 10 minutes
    }

    internal val context: JMSContext
        get() = factory.createContext(
            config.username,
            config.password,
            JMSContext.SESSION_TRANSACTED
        )

    fun depth(queue: MQQueue): Int {
        mqLog.debug("Checking queue depth for ${queue.baseQueueName}")
        return transaction { ctx ->
            ctx.createBrowser(queue).use { browse ->
                browse.enumeration.toList().size
            }
        }
    }

    internal fun <T : Any> transacted(ctx: JMSContext, block: () -> T): T {
        mqLog.debug("MQ transaction created {}", ctx)

        val result = runCatching {
            block()
        }.onSuccess {
            ctx.commit()
            mqLog.debug("MQ transaction committed {}", ctx)
        }.onFailure {
            ctx.rollback()
            mqLog.error("MQ transaction rolled back {}, please check secureLogs or BOQ (backout queue)", ctx)
            secureLog.error("MQ transaction rolled back {}", ctx, it)
        }

        return result.getOrThrow()
    }

    fun <T : Any> transaction(block: (JMSContext) -> T): T =
        context.use { ctx ->
            transacted(ctx) {
                block(ctx)
            }
        }
}

data class MQConfig(
    val host: String,
    val port: Int,
    val channel: String,
    val manager: String,
    val username: String,
    val password: String,
)
