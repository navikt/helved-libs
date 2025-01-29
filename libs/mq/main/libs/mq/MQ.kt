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
import javax.jms.JMSContext
import javax.jms.JMSProducer
import javax.jms.MessageListener
import javax.jms.TextMessage

private val mqLog = logger("mq")

class MQProducer(
    private val mq: MQ,
    private val queue: MQQueue,
) {
    fun produce(
        message: String,
        config: JMSProducer.() -> Unit = {},
    ) {
        mqLog.info("Producing message on ${queue.baseQueueName}")
        mq.transaction { ctx ->
            ctx.clientID = UUID.randomUUID().toString()
            val producer = ctx.createProducer().apply(config)
            val message = ctx.createTextMessage(message)
            getTraceparent()?.let { message.setJMSCorrelationID(it) }
            producer.send(queue, message)
        }
    }
}

internal interface MQListener {
    fun onMessage(message: TextMessage)
}

abstract class MQConsumer(
    private val mq: MQ,
    private val queue: MQQueue,
) : AutoCloseable, MQListener {
    private val context = mq.context.apply {
        autoStart = false
    }

    private val consumer = context.createConsumer(queue).apply {
        messageListener = MessageListener {
            mqLog.info("Consuming message on ${queue.baseQueueName}")
            mq.transacted(context) {
                val span = it.getJMSCorrelationID()?.let { id ->
                    val parentCtx = propagateSpan(id)
                    tracer.spanBuilder(queue.baseQueueName)
                        .setParent(parentCtx)
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
