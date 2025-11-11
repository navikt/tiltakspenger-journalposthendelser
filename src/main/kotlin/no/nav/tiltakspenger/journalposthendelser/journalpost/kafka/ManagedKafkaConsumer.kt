package no.nav.tiltakspenger.journalposthendelser.journalpost.kafka

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.tiltakspenger.libs.kafka.ConsumerStatus
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
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
        while (running) {
            try {
                consumer.subscribe(listOf(topic))
                poll(consumer)
            } catch (e: WakeupException) {
                log.info { "Consumer for $topic is exiting" }
                stop()
            } catch (t: Throwable) {
                log.error(t) { "Something went wrong with consumer for topic $topic" }
                consumer.unsubscribe()
                delay(5_000)
            }
        }
    }

    private suspend fun poll(consumer: KafkaConsumer<K, V>) {
        while (running) {
            if (status.isFailure) {
                log.info {
                    "Consumer status for topic $topic is failure, " +
                        "delaying ${status.backoffDuration}ms before retrying"
                }
                delay(status.backoffDuration)
            }

            try {
                val records = consumer.poll(Duration.ofMillis(1000))
                if (!records.isEmpty) {
                    log.debug { "Consumer for $topic polled ${records.count()} records." }
                }

                records.forEach { record ->
                    process(record)
                    status.success()
                }
                consumer.commitSync()
            } catch (t: Throwable) {
                log.error(t) { t.message }
                status.failure()
                throw t
            }
        }
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
}
