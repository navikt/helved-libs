package libs.kafka.stream

import libs.kafka.Topic
import libs.kafka.stream.MappedStream
import org.apache.kafka.streams.kstream.Branched
import org.apache.kafka.streams.kstream.KStream

class BranchedKStream<T : Any> internal constructor(
    private val topic: Topic<T>,
    private val stream: org.apache.kafka.streams.kstream.BranchedKStream<String, T>,
    private val namedSupplier: () -> String,
) {
    private var nextBranchNumber: Int = 1
        get() = field++

    fun branch(
        predicate: (T) -> Boolean,
        consumed: ConsumedStream<T>.() -> Unit,
    ): BranchedKStream<T> {
        val namedBranch = "-branch-$nextBranchNumber"
        val internalPredicate = internalPredicate(predicate)
        val internalBranch = internalBranch(consumed, namedBranch) { "via$namedBranch-${namedSupplier()}" }
        stream.branch(internalPredicate, internalBranch)
        return this
    }

    fun default(consumed: ConsumedStream<T>.() -> Unit) {
        val namedBranch = "-branch-default"
        val internalBranch = internalBranch(consumed, namedBranch) { "via$namedBranch-${namedSupplier()}" }
        stream.defaultBranch(internalBranch)
    }

    private fun internalBranch(
        branch: (ConsumedStream<T>) -> Unit,
        namedBranch: String,
        namedSupplier: () -> String,
    ): Branched<String, T> = Branched.withConsumer(
        { chain: KStream<String, T> -> branch(ConsumedStream(topic, chain, namedSupplier)) },
        namedBranch
    )
}

class BranchedMappedKStream<S: Any, T : Any> internal constructor(
    private val topic: Topic<S>,
    private val stream: org.apache.kafka.streams.kstream.BranchedKStream<String, T>,
    private val namedSupplier: () -> String,
) {
    private var nextBranchNumber: Int = 1
        get() = field++

    fun branch(
        predicate: (T) -> Boolean,
        consumed: MappedStream<S, T>.() -> Unit,
    ): BranchedMappedKStream<S, T> {
        val namedBranch = "-branch-$nextBranchNumber"
        val internalPredicate = internalPredicate(predicate)
        val internalBranch = internalBranch(consumed, namedBranch) { "via$namedBranch-${namedSupplier()}" }
        stream.branch(internalPredicate, internalBranch)
        return this
    }

    fun default(consumed: MappedStream<S, T>.() -> Unit) {
        val namedBranch = "-branch-default"
        val internalBranch = internalBranch(consumed, namedBranch) { "via$namedBranch-${namedSupplier()}" }
        stream.defaultBranch(internalBranch)
    }

    private fun internalBranch(
        branch: (MappedStream<S, T>) -> Unit,
        namedBranch: String,
        namedSupplier: () -> String,
    ): Branched<String, T> = Branched.withConsumer(
        { chain: KStream<String, T> -> branch(MappedStream(topic, chain, namedSupplier)) },
        namedBranch
    )
}

private fun <T> internalPredicate(
    predicate: (T) -> Boolean,
): (String, T) -> Boolean = { _: String, value: T ->
    predicate(value)
}
