package dk.ku.di.dms.vms.sdk.embed.client.external;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.ByteArraySerializer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class KafkaExternalClient implements IExternalSystemClient {

    private static final System.Logger LOGGER = System.getLogger(KafkaExternalClient.class.getName());

    private final String alias;
    private final KafkaProducer<byte[], byte[]> producer;

    public KafkaExternalClient(String alias, Properties config) {
        this.alias = Objects.requireNonNull(alias, "alias cannot be null");
        Objects.requireNonNull(config, "Kafka config cannot be null");

        // --- Fill sensible defaults if missing (byte[] serializers) ---
        config.putIfAbsent("key.serializer", ByteArraySerializer.class.getName());
        config.putIfAbsent("value.serializer", ByteArraySerializer.class.getName());

        // 打印关键配置，便于排查
        String bs = String.valueOf(config.getProperty("bootstrap.servers"));
        String acks = String.valueOf(config.getProperty("acks"));
        String idem = String.valueOf(config.getProperty("enable.idempotence"));
        String comp = String.valueOf(config.getProperty("compression.type"));
        LOGGER.log(System.Logger.Level.INFO,
                () -> "[KafkaExternalClient] Init alias=" + alias +
                        ", bootstrap.servers=" + bs +
                        ", acks=" + acks +
                        ", enable.idempotence=" + idem +
                        ", compression.type=" + comp);

        this.producer = new KafkaProducer<>(config);
    }

    @Override
    public String systemAlias() {
        return alias;
    }

    @Override
    public CompletionStage<Void> send(String destination, String payload) {
        return doSend(destination, payload, null);
    }

    @Override
    public CompletionStage<Void> send(String destination, String payload, Map<String, String> headers) {
        return doSend(destination, payload, headers);
    }

    private CompletionStage<Void> doSend(String topic, String payload, Map<String, String> headers) {
        if (topic == null || topic.isBlank()) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("destination (topic) cannot be null/blank"));
        }

        byte[] valueBytes = payload == null ? new byte[0] : payload.getBytes(StandardCharsets.UTF_8);
        ProducerRecord<byte[], byte[]> record = new ProducerRecord<>(topic, null, valueBytes);

        if (headers != null && !headers.isEmpty()) {
            headers.forEach((k, v) -> {
                if (k != null && v != null) {
                    record.headers().add(k, v.getBytes(StandardCharsets.UTF_8));
                }
            });
        }

        // 发送前日志（只截断展示payload前256字符，避免刷屏）
        LOGGER.log(System.Logger.Level.INFO,
                () -> "[KafkaExternalClient] sending -> alias=" + alias +
                        ", topic=" + topic +
                        ", bytes=" + valueBytes.length +
                        (headers == null || headers.isEmpty() ? "" : (", headers=" + headers)) +
                        ", preview=" + preview(payload, 256));

        CompletableFuture<Void> fut = new CompletableFuture<>();

        producer.send(record, new Callback() {
            @Override
            public void onCompletion(RecordMetadata md, Exception ex) {
                if (ex == null) {
                    LOGGER.log(System.Logger.Level.INFO,
                            () -> "[KafkaExternalClient] send OK -> alias=" + alias +
                                    ", topic=" + md.topic() +
                                    ", partition=" + md.partition() +
                                    ", offset=" + md.offset() +
                                    ", timestamp=" + md.timestamp());
                    fut.complete(null);
                } else {
                    // 打完整堆栈，问题一眼看穿（比如 UnknownTopicOrPartition / Network 异常）
                    LOGGER.log(System.Logger.Level.ERROR,
                            "[KafkaExternalClient] send FAILED -> alias=" + alias +
                                    ", topic=" + topic + ", err=" + ex.getMessage(), ex);
                    fut.completeExceptionally(ex);
                }
            }
        });

        return fut;
    }

    @Override
    public void close() {
        LOGGER.log(System.Logger.Level.INFO, () -> "[KafkaExternalClient] Closing producer for alias=" + alias);
        try {
            // 避免长时间阻塞，这里给个合理超时
            producer.flush();
            producer.close(Duration.ofSeconds(5));
            LOGGER.log(System.Logger.Level.INFO, () -> "[KafkaExternalClient] Closed producer for alias=" + alias);
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.ERROR,
                    () -> "[KafkaExternalClient] Error while closing for alias=" + alias + ": " + e.getMessage(), e);
        }
    }

    private static String preview(String s, int max) {
        if (s == null) return "null";
        if (s.length() <= max) return s;
        return s.substring(0, max) + "...(" + s.length() + " chars)";
    }
}
