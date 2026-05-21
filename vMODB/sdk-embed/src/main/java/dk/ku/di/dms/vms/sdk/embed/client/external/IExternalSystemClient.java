package dk.ku.di.dms.vms.sdk.embed.client.external;

import dk.ku.di.dms.vms.sdk.embed.handler.ExternalEventRecord;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * Contract for any external messaging client (e.g., Kafka/HTTP/Rabbit).
 */
public interface IExternalSystemClient extends AutoCloseable {

    /**
     * Logical alias used in annotations/config, e.g. "kafka_main".
     */
    String systemAlias();

    /**
     * Send a message to a destination (topic/queue/endpoint).
     * The payload is a serialized String (JSON or similar).
     * Implementations should be non-blocking and return a CompletionStage.
     */
    CompletionStage<Void> send(String destination, String payload);

    /**
     * Optional headers if the underlying system supports them (Kafka headers/HTTP headers).
     * Implementations may ignore headers if unsupported.
     */
    default CompletionStage<Void> send(String destination, String payload, Map<String, String> headers) {
        // Default: ignore headers
        return send(destination, payload);
    }
    default CompletionStage<Void> sendBatch(String destination, Collection<String> payloads) {
        return java.util.concurrent.CompletableFuture.runAsync(() -> {
            for (String p : payloads) {
                send(destination, p);
            }
        });
    }

    /**
     * Called on shutdown. Implementations should close underlying resources.
     */
    @Override
    void close();
}
