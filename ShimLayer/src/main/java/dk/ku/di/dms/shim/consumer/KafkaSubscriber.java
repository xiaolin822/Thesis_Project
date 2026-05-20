package dk.ku.di.dms.shim.consumer;

import dk.ku.di.dms.shim.model.CausalEvent;
import dk.ku.di.dms.shim.model.EventID;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

public class KafkaSubscriber {
    private final KafkaConsumer<String, String> consumer;

    public KafkaSubscriber(String bootstrapServers) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "shim-layer-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        this.consumer = new KafkaConsumer<>(props);
    }

    public void startListening(List<String> topics, Consumer<CausalEvent> onEventReceived) {
        consumer.subscribe(topics);
        System.out.println("[Kafka] start subscribe topics: " + topics);

        new Thread(() -> {
            try {
                while (true) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                    for (ConsumerRecord<String, String> record : records) {
                        EventID id = null;
                        if (record.key() != null) {
                            id = EventID.fromString(record.key());
                        }
                        Set<EventID> after = new HashSet<>();

                        // get Causal labels from Kafka Headers
                        for (Header header : record.headers()) {
                            byte[] valueBytes = header.value();
                            if (valueBytes == null) continue;
                            String value = new String(valueBytes);

                            if ("causal_tags".equals(header.key())) {
                                if (!value.isEmpty()) {
                                    for (String dep : value.split(",")) {
                                        after.add(EventID.fromString(dep));
                                    }
                                }
                            }
                        }

                        if (id != null) {
                            CausalEvent event = new CausalEvent(id, after, record.topic(), record.value());
                            onEventReceived.accept(event);
                        }
                    }
                }
            } finally {
                consumer.close();
            }
        }).start();
    }
}