package com.databytes.examples.kafka;

import java.time.Instant;
import java.util.Properties;
import java.util.UUID;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * Raw Apache Kafka producer. This is the hand-written version of what Day 7 did with
 * the kafka-console-producer CLI; Day 10 replaces it with SmallRye Reactive Messaging
 * (@Outgoing channels).
 *
 * IMPORTANT: the broker's HOST listener is published on localhost:29092. The in-container
 * listener (kafka:9092 / 9092) is NOT reachable from the laptop, so Java on the host must
 * use localhost:29092.
 */
public final class ProducerMain {

    private static final String BOOTSTRAP_SERVERS = "localhost:29092";
    private static final String TOPIC = "applicant-registered";

    private ProducerMain() {
    }

    public static void main(String[] args) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");

        System.out.println("== Kafka producer -> " + BOOTSTRAP_SERVERS + " topic=" + TOPIC + " ==");

        String[][] applicants = {
                {"1001", "Amira Haddad", "TN-4820193", "Tunisia", "amira.haddad@example.com"},
                {"1002", "Jonas Berg", "SE-9931028", "Sweden", "jonas.berg@example.com"},
                {"1003", "Wei Chen", "CN-5561200", "China", "wei.chen@example.com"},
                {"1004", "Lucia Rossi", "IT-7788341", "Italy", "lucia.rossi@example.com"},
                {"1005", "Omar Farouk", "EG-1029384", "Egypt", "omar.farouk@example.com"},
        };

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            for (String[] a : applicants) {
                String applicantId = a[0];
                String value = buildEventJson(applicantId, a[1], a[2], a[3], a[4]);

                ProducerRecord<String, String> record =
                        new ProducerRecord<>(TOPIC, applicantId, value);

                // Block on the future so we can print the assigned partition and offset.
                RecordMetadata md = producer.send(record).get();
                System.out.println("    sent key=%s -> partition=%d offset=%d".formatted(
                        applicantId, md.partition(), md.offset()));
            }
            producer.flush();
        } catch (Exception e) {
            throw new RuntimeException("Failed to produce records", e);
        }

        System.out.println("== Done ==");
    }

    private static String buildEventJson(String applicantId, String fullName, String nationalId,
                                         String country, String email) {
        // Matches the ApplicantRegisteredEvent contract published by the KYC service.
        String template = """
                {"applicantId":%s,"fullName":"%s","nationalId":"%s","country":"%s","email":"%s","status":"PENDING","occurredAt":"%s","correlationId":"%s"}""";
        return template.formatted(
                applicantId, fullName, nationalId, country, email,
                Instant.now().toString(), UUID.randomUUID().toString());
    }
}
