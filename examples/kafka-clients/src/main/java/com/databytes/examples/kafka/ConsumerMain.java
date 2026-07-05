package com.databytes.examples.kafka;

import java.time.Duration;
import java.util.List;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

/**
 * Raw Apache Kafka consumer. This is the hand-written version of what Day 7 did with
 * the kafka-console-consumer CLI; Day 10 replaces it with SmallRye Reactive Messaging
 * (@Incoming channels).
 *
 * Manual offset commits (enable.auto.commit=false + commitSync) and earliest reset so a
 * fresh group replays existing records. Terminates on its own after ~10s of idle polls.
 *
 * IMPORTANT: use the HOST listener localhost:29092 (kafka:9092 is not reachable from the laptop).
 */
public final class ConsumerMain {

    private static final String BOOTSTRAP_SERVERS = "localhost:29092";
    private static final String TOPIC = "applicant-registered";
    private static final String GROUP_ID = "examples-screening";

    // Poll for 1s at a time; 10 consecutive empty polls (~10s) means "idle" -> exit.
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(1);
    private static final int MAX_EMPTY_POLLS = 10;

    private ConsumerMain() {
    }

    public static void main(String[] args) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        System.out.println("== Kafka consumer <- " + BOOTSTRAP_SERVERS
                + " topic=" + TOPIC + " group=" + GROUP_ID + " ==");
        System.out.println("   (will exit after ~" + MAX_EMPTY_POLLS + "s with no new records)");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(TOPIC));

            int emptyPolls = 0;
            while (emptyPolls < MAX_EMPTY_POLLS) {
                ConsumerRecords<String, String> records = consumer.poll(POLL_TIMEOUT);
                if (records.isEmpty()) {
                    emptyPolls++;
                    continue;
                }
                emptyPolls = 0;
                for (ConsumerRecord<String, String> record : records) {
                    System.out.println("    partition=%d offset=%d key=%s value=%s".formatted(
                            record.partition(), record.offset(), record.key(), record.value()));
                }
                // Manual commit: only advance offsets after we have processed the batch.
                consumer.commitSync();
            }
        }

        System.out.println("== Idle; exiting ==");
    }
}
