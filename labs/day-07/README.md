# Day 7 — Drive Kafka from the CLI, then from Java

> Starting point: this repository with the dev infrastructure up. No `starter/` — you practise
> against the real broker. No application code until the final step, where you run a plain-Java
> client against the same broker.

## Objective

Operate Apache Kafka entirely from its command-line tools, run from a shell **inside the broker's
container** — the same move you used for `psql` on Day 6. Create a partitioned topic, produce keyed
messages, consume and replay them, read a consumer group's offsets and lag, trigger a rebalance, reset
offsets for a deliberate replay, and flip a topic to compaction. Then run the plain-Java producer and
consumer to see the client the framework will hide on Day 10.

## Concepts in play

- [Why events, and when not to](../../docs/content/day-07/index.md#why-events-and-when-not-to)
- [Kafka in four words: topics, partitions, groups, offsets](../../docs/content/day-07/index.md#kafka-in-four-words-topics-partitions-groups-offsets)
- [Retention: the log is bounded](../../docs/content/day-07/index.md#retention-the-log-is-bounded)
- [Driving Kafka by hand](../../docs/content/day-07/index.md#driving-kafka-by-hand)
- [Serialization & schema](../../docs/content/day-07/index.md#module-2-serialization-schema)
- [Delivery, reliability & durability](../../docs/content/day-07/index.md#module-3-delivery-reliability-durability)
- [Operations & troubleshooting](../../docs/content/day-07/index.md#module-4-operations-troubleshooting)
- [Kafka from Java](../../docs/content/day-07/index.md#module-5-kafka-from-java)

## The command pattern

Every CLI command runs by **full path** inside the `kafka` container (the scripts are not on `PATH`),
addressing the broker as `localhost:9092`:

```bash
docker compose -f infra/compose.yaml exec kafka \
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```

## Steps

1. **Bring it up.** Start the infrastructure, then confirm the broker answers (an empty result is
   success — no topics yet):
   ```bash
   docker compose -f infra/compose.yaml up -d postgres kafka kafka-ui
   docker compose -f infra/compose.yaml exec kafka \
     /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
   ```
2. **Create and inspect a topic** with 3 partitions:
   ```bash
   docker compose -f infra/compose.yaml exec kafka \
     /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
     --create --topic applicant-registered --partitions 3 --replication-factor 1
   docker compose -f infra/compose.yaml exec kafka \
     /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
     --describe --topic applicant-registered
   ```
   Confirm `--describe` shows three partitions (0, 1, 2), each with `Leader`, `Replicas`, and `Isr` —
   the durability vocabulary from Module 3 (here all `1`, because there is a single broker).
3. **Produce keyed messages by hand** (type `key:value`, finish with Ctrl-D). Send `1001` twice so you
   can prove same-key-same-partition:
   ```bash
   docker compose -f infra/compose.yaml exec -it kafka \
     /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 \
     --topic applicant-registered --property parse.key=true --property key.separator=:
   ```
   ```text
   1001:applicant 1001 registered
   1003:applicant 1003 registered
   1005:applicant 1005 registered
   1001:applicant 1001 cleared
   ```
4. **Consume from the beginning**, showing partition and key, then stop with Ctrl-C:
   ```bash
   docker compose -f infra/compose.yaml exec kafka \
     /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
     --topic applicant-registered --from-beginning \
     --property print.partition=true --property print.key=true
   ```
   Note that both `1001` messages share a partition. **Run it again** and confirm every message
   reappears — a topic is a log, not a queue.
5. **Read as a group and inspect lag.** Consume once with `--group screening --from-beginning`, stop
   it, then describe the group:
   ```bash
   docker compose -f infra/compose.yaml exec kafka \
     /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
     --describe --group screening
   ```
   Read `CURRENT-OFFSET`, `LOG-END-OFFSET`, and `LAG`. Produce more messages, re-describe, and watch
   `LAG` rise; restart the consumer and watch it drain to 0.
6. **Trigger a rebalance.** Start two consumers in the **same** group `workers` (two terminals,
   identical command, no `--from-beginning`), then produce keyed messages from a third terminal.
   Confirm each message arrives in only one consumer. Kill one consumer and watch the other take over
   all partitions.
7. **Reset offsets for a deliberate replay.** Stop the `screening` group's consumers, preview the
   reset with `--dry-run`, then commit it and re-consume from the start:
   ```bash
   docker compose -f infra/compose.yaml exec kafka \
     /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
     --group screening --topic applicant-registered --reset-offsets --to-earliest --dry-run
   # swap --dry-run for --execute when the preview looks right
   ```
8. **Change retention live, then compact.** Use `kafka-configs.sh` to shorten retention, then turn the
   topic into a "current state per key" changelog:
   ```bash
   docker compose -f infra/compose.yaml exec kafka \
     /opt/kafka/bin/kafka-configs.sh --bootstrap-server localhost:9092 \
     --alter --topic applicant-registered --add-config retention.ms=600000
   docker compose -f infra/compose.yaml exec kafka \
     /opt/kafka/bin/kafka-configs.sh --bootstrap-server localhost:9092 \
     --describe --topic applicant-registered
   ```
   Reason about which of your two `1001` messages a compacted topic would keep.
9. **Cross-check in the Kafka UI** at <http://localhost:8081>: find `applicant-registered`, click into
   a partition, read a message (key and value), and view the `workers` group's lag.
10. **Run the Java client.** In one terminal start the consumer, in another run the producer, then
    cross-check the group with the CLI. Note the **host** port `29092` (not `9092`):
    ```bash
    cd examples/kafka-clients
    mvn -q compile exec:java -Dexec.mainClass=com.databytes.examples.kafka.ConsumerMain
    # in a second shell:
    mvn -q compile exec:java
    ```

## Acceptance criteria

- [ ] An `applicant-registered` topic whose `--describe` shows 3 partitions with `Leader`/`Replicas`/`Isr`.
- [ ] Keyed messages produced by hand; both `1001` messages land in the **same** partition.
- [ ] A replay (`--from-beginning` run twice) proving messages are not removed by reading.
- [ ] A consumer group with a non-zero `LAG` that you watch drain to 0.
- [ ] A rebalance you caused on purpose by adding then removing a second consumer.
- [ ] An offset reset (`--reset-offsets --to-earliest`) you previewed with `--dry-run`.
- [ ] The Java producer and consumer exchanged `applicant-registered` records via `localhost:29092`.

## Stretch goals

- Reset a group to a point in time with `--reset-offsets --to-datetime <ISO-8601>` instead of
  `--to-earliest`, and reason about how it mirrors Day 6's point-in-time recovery.
- Set `min.insync.replicas` on the topic and reason about why a producer with `acks=all` would fail
  on this single-broker cluster if it were `2`.
- Produce several values for one key on a compacted topic, force a consume from the beginning, and
  observe which survive.
- Predict, before consuming, which partition a new key will land in, then verify with
  `print.partition=true`.
- Point the Java producer at `localhost:9092` on purpose and watch it hang — the classic wrong-listener
  trap from Module 4.
