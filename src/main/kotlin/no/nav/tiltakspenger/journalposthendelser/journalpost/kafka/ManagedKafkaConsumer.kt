package no.nav.tiltakspenger.journalposthendelser.journalpost.kafka

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.tiltakspenger.libs.kafka.ConsumerStatus
import org.apache.kafka.clients.consumer.CommitFailedException
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.WakeupException
import java.time.Duration

class ManagedKafkaConsumer<K, V>(
    private val kanLoggeKey: Boolean = true,
    private val topic: String,
    private val config: Map<String, *>,
    private val consume: suspend (key: K, value: V) -> Unit,
) {
    private val log = KotlinLogging.logger {}
    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val offsetsToCommit = mutableMapOf<TopicPartition, OffsetAndMetadata>()

    private var running = false

    val status: ConsumerStatus = ConsumerStatus()

    init {
        Runtime.getRuntime().addShutdownHook(
            Thread {
                log.info { "Shutting down Kafka consumer" }
                stop()
            },
        )
    }

    fun run() = scope.launch {
        log.info { "Starting consumer for topic: $topic (not from libs)" }
        running = true

        KafkaConsumer<K, V>(config).use { consumer ->
            subscribe(consumer)
        }
    }

    fun start() = run()

    fun stop() {
        log.info { "Stopping consumer for topic: $topic" }
        running = false
    }

    private suspend fun subscribe(consumer: KafkaConsumer<K, V>) {
        try {
            consumer.subscribe(listOf(topic), rebalanceListener(consumer))

            while (running) {
                poll(consumer)
            }
        } catch (e: WakeupException) {
            log.info { "Consumer for $topic is exiting" }
            stop()
        } catch (e: CommitFailedException) {
            log.error(e) { "Consumer for $topic cannot commit offsets. Unsubscribing and then retrying" }
            consumer.unsubscribe()
            delay(10_000)
        } catch (t: Throwable) {
            log.error(t) { "Something went wrong with consumer for topic $topic" }
            throw t
        }
    }

    private suspend fun poll(consumer: KafkaConsumer<K, V>) {
        if (status.isFailure) {
            log.info {
                "Consumer status for topic $topic is failure, " +
                    "delaying ${status.backoffDuration}ms before retrying"
            }
            delay(status.backoffDuration)
        }

        try {
            val records = consumer.poll(Duration.ofMillis(1000))

            seekToEarliestOffsets(records, consumer)

            if (!records.isEmpty) {
                log.debug { "Consumer for $topic polled ${records.count()} records." }
            }

            records.forEach { record ->
                process(record)

                val partition = TopicPartition(record.topic(), record.partition())
                val offset = OffsetAndMetadata(record.offset() + 1)

                offsetsToCommit[partition] = offset
                status.success()
            }
        } catch (t: Throwable) {
            log.error(t) { t.message }
            status.failure()
        } finally {
            commitOffsets(consumer)
        }
    }

    private fun commitOffsets(consumer: KafkaConsumer<K, V>) {
        if (offsetsToCommit.isNotEmpty()) {
            offsetsToCommit.forEach { (partition, offset) -> consumer.seek(partition, offset) }
            consumer.commitSync(offsetsToCommit)

            log.debug { "Committed offsets $offsetsToCommit" }
            offsetsToCommit.clear()
        }
    }

    private fun seekToEarliestOffsets(records: ConsumerRecords<K, V>, consumer: KafkaConsumer<K, V>) {
        val offsetMap = mutableMapOf<TopicPartition, OffsetAndMetadata>()

        records.forEach { record ->
            val topicPartition = TopicPartition(record.topic(), record.partition())
            val offsetAndMetadata = OffsetAndMetadata(record.offset())

            val storedOffset = offsetMap[topicPartition]

            if (storedOffset == null || offsetAndMetadata.offset() < storedOffset.offset()) {
                offsetMap[topicPartition] = offsetAndMetadata
            }
        }

        offsetMap.forEach { consumer.seek(it.key, it.value) }
    }

    private suspend fun process(record: ConsumerRecord<K, V>) {
        // noen topics bruker fnr som key, og da skal ikke disse logges til vanlig logg
        val keyLogStatement = if (kanLoggeKey) {
            "key=${record.key()} "
        } else {
            ""
        }
        try {
            consume(record.key(), record.value())
            log.debug {
                "Consumed record for " +
                    "topic=${record.topic()} " +
                    keyLogStatement +
                    "partition=${record.partition()} " +
                    "offset=${record.offset()}"
            }
        } catch (t: Throwable) {
            val msg = "Failed to consume record for " +
                "topic=${record.topic()} " +
                keyLogStatement +
                "partition=${record.partition()} " +
                "offset=${record.offset()}"
            throw ConsumerProcessingException(msg, t)
        }
    }

    class ConsumerProcessingException(
        msg: String,
        cause: Throwable?,
    ) : RuntimeException(msg, cause)

    private fun rebalanceListener(consumer: KafkaConsumer<K, V>) = object : ConsumerRebalanceListener {
        override fun onPartitionsRevoked(partitions: MutableCollection<TopicPartition>) {
            log.info { "Partitions revoked $partitions, committing offsets" }
            commitOffsets(consumer)
        }

        override fun onPartitionsAssigned(partitions: MutableCollection<TopicPartition>) {
            log.info { "Partitions assigned $partitions" }
        }
    }
}
