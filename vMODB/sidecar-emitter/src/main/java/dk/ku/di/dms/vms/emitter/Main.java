package dk.ku.di.dms.vms.emitter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dk.ku.di.dms.vms.emitter.adapter.VmsDecoder;
import dk.ku.di.dms.vms.emitter.adapter.VmsLogReader;
import dk.ku.di.dms.vms.emitter.engine.CausalTag;
import dk.ku.di.dms.vms.emitter.model.KafkaEvent;
import dk.ku.di.dms.vms.emitter.model.RawLogRecord;
import dk.ku.di.dms.vms.emitter.model.EventID;

import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.header.internals.RecordHeader;


import java.util.stream.Collectors;
import java.io.File;
import java.util.*;

public class Main {

    private static KafkaProducer<String, String> producer;
    private static Map<String, String> topologyMap;
    private static final CausalTag causalEngine = new CausalTag();

    // 2. Buffer: BatchID -> List of Events to emit
    private static final TreeMap<Long, List<KafkaEvent>> emitBuffer = new TreeMap<>();

    public static void main(String[] args) throws Exception {
        String vmsId = System.getenv("VMS_ID");
        String logBaseDir = System.getenv().getOrDefault("VMS_LOG_BASE_DIR", "/tmp/");
        String configPath = System.getenv().getOrDefault("TOPOLOGY_CONFIG", "/tmp/map.json");
        if (vmsId == null) {
            System.err.println("Error: No VMSID!");
            System.exit(1);
        }

        System.out.println("Starting Sidecar Emitter for VMS: " + vmsId);
        topologyMap = new ObjectMapper().readValue(
                new File(configPath),
                new TypeReference<Map<String, String>>() {}
        );

        // initialize Kafka Producer
        Properties props = new Properties();
        props.put("bootstrap.servers",
                System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092")
        );
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("transactional.id", "sidecar-tx-" + vmsId);
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 60000);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000);
        props.put("enable.idempotence", "true");

        producer = new KafkaProducer<>(props);
        Runtime.getRuntime().addShutdownHook(
                new Thread(() -> {
                    if (producer != null) {
                        producer.close();
                    }
                })
        );
        producer.initTransactions();

        if (!logBaseDir.endsWith("/")) logBaseDir += "/";
        String logPath = logBaseDir + vmsId + "_log.txt";
        System.out.println("Watching log file: " + logPath);
        VmsLogReader reader = new VmsLogReader(logPath);

        // KafkaAdapter kafkaAdapter = new KafkaAdapter("localhost:9092");

        //  tail -f
        reader.readStream(line -> {
            RawLogRecord record = VmsDecoder.decode(line);
            if (record == null) return;

            if ("LOG".equals(record.msgType())) {
                handleLogRecord(record);
            } else if ("COMMIT".equals(record.msgType())) {
                handleCommitRecord(record);
            }
        });
    }

    private static void handleLogRecord(RawLogRecord record) {

        Set<EventID> causalTags = causalEngine.computeCausalTags(record, topologyMap);

        if (record.emit()) {
            KafkaEvent event = new KafkaEvent(
                    record.tid(),
                    record.vmsId(),
                    record.topic(),
                    record.payload(),
                    causalTags,
                    record.batchId()
            );

            emitBuffer.computeIfAbsent(record.batchId(), k -> new ArrayList<>()).add(event);

            System.out.printf("[Buffer] Batch %d: Tid %d added to queue.%n",
                    record.batchId(), record.tid());
        }
    }

    private static void handleCommitRecord(RawLogRecord record) {
        long commitBatchId = record.batchId();
        long maxTidInBatch = record.tid();

        System.out.printf("[Commit] Received COMMIT for Batch %d. Flushing buffer...%n", commitBatchId);

        // BatchID <= commitBatchId
        SortedMap<Long, List<KafkaEvent>> toFlush = emitBuffer.headMap(commitBatchId, true);
        if (toFlush.isEmpty()) return;

        try {
            for (long batchId : new ArrayList<>(toFlush.keySet())) {
                List<KafkaEvent> events = toFlush.get(batchId);
                if (batchId == commitBatchId) {
                    long actualMaxTid = events.stream()
                            .mapToLong(KafkaEvent::tid)
                            .max().orElse(-1L);

                    if (actualMaxTid < maxTidInBatch) {
                        System.err.printf("!!! [WARNING] Batch %d expected maxTid %d, but only found %d in buffer. Waiting...%n",
                                batchId, maxTidInBatch, actualMaxTid);
                        return;
                    }
                }

                producer.beginTransaction();
                try {
                    for (KafkaEvent event : events) {
                        // Construct Causal Tags Header
                        // "cart:101,inventory:202"
                        String tagsString = event.causalTags().stream()
                                .map(EventID::toString)
                                .collect(Collectors.joining(","));

                        EventID currentId = new EventID(event.vmsId(), event.tid());

                        ProducerRecord<String, String> pr = new ProducerRecord<>(
                                event.topic(), // Topic
                                null,               // Partition
                                String.valueOf(currentId.toString()), // Key
                                event.payload()     // Value
                        );

                        // put CausalTag in Header
                        pr.headers().add(new RecordHeader("causal_tags", tagsString.getBytes()));
                        // put BatchId in Header
                        pr.headers().add(new RecordHeader("vms_batch_id", String.valueOf(batchId).getBytes()));

                        // producer buffer
                        //producer.send(pr, (metadata, exception) -> {
                          //  if (exception != null) {
                            //    System.err.println("Send failed for TID " + event.tid());
                            //}
                        //});
                        producer.send(pr).get();
                    }
                    producer.flush();

                    producer.commitTransaction();
                    emitBuffer.remove(batchId);
                    System.out.printf(">>> [Kafka] Batch %d committed to Kafka.%n", batchId);

                } catch (Exception e) {
                    // if this batch fails, abort
                    producer.abortTransaction();
                    System.err.println("Batch " + batchId + " failed, aborted.");
                    throw e;
                }
            }

        } catch (Exception e) {
            System.err.println("Critical Error during Batch Flush: " + e.getMessage());
        }
    }
}