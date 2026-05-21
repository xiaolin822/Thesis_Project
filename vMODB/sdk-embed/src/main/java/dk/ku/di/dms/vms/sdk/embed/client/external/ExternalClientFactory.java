package dk.ku.di.dms.vms.sdk.embed.client.external;

import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry/factory for external system clients.
 * - Register clients at startup (from app.properties)
 * - Lookup by alias at runtime in VmsEventHandler
 * Expected config in app.properties (per microservice)
 */
public class ExternalClientFactory {

    private final Map<String, IExternalSystemClient> clients = new ConcurrentHashMap<>();

    public void register(IExternalSystemClient client) {
        Objects.requireNonNull(client, "client must not be null");
        String alias = Objects.requireNonNull(client.systemAlias(), "client.systemAlias() must not be null");
        clients.put(alias, client);
    }

    public IExternalSystemClient get(String alias) {
        IExternalSystemClient c = clients.get(alias);
        if (c == null) {
            throw new IllegalArgumentException("No external client registered for alias: " + alias);
        }
        return c;
    }

    public boolean exists(String alias) {
        return clients.containsKey(alias);
    }

    public void unregister(String alias) {
        IExternalSystemClient c = clients.remove(alias);
        if (c != null) {
            try { c.close(); } catch (Exception ignore) {}
        }
    }

    public Set<String> aliases() {
        return Collections.unmodifiableSet(clients.keySet());
    }

    public void closeAll() {
        for (IExternalSystemClient c : clients.values()) {
            try { c.close(); } catch (Exception ignore) {}
        }
        clients.clear();
    }

    /**
     * Load app.properties from the current thread context ClassLoader.
     * If not found, does nothing and returns empty Properties.
     */
    public void  loadFromClasspathAndRegister(String resourceName) {
        Properties props = new Properties();
        try (InputStream in = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(resourceName)) {
            if (in != null) {
                props.load(in);
                loadFromProperties(props);
            }
        } catch (Exception e) {
            // Optional: log or rethrow as runtime
            System.err.println("[ExternalClientFactory] Failed to load " + resourceName + ": " + e.getMessage());
        }
    }

    /**
     * Register clients based on the given Properties.
     * Expects the "external.systems" CSV and per-alias "external.system.{alias}.*" entries.
     */
    public void loadFromProperties(Properties props) {
        if (props == null) return;

        String csv = props.getProperty("external.systems", "").trim();
        if (csv.isEmpty()) return;

        for (String aliasRaw : csv.split(",")) {
            String alias = aliasRaw.trim();
            if (alias.isEmpty()) continue;

            String prefix = "external.system." + alias + ".";
            String type = get(props, prefix + "type", "log").toLowerCase();

            switch (type) {
                case "kafka" -> register(buildKafkaClient(alias, props, prefix));
                case "log"   -> register(new LoggingClient(alias));
                // You can add more types here: "http", "rabbitmq", etc.
                default      -> register(new LoggingClient(alias)); // fallback: log-only client
            }
        }
    }

    /* =========================
     * Client builders (by type)
     * ========================= */

    private IExternalSystemClient buildKafkaClient(String alias, Properties props, String prefix) {
        // Collect Kafka producer properties
        Properties kp = new Properties();
        // Required
        kp.put("bootstrap.servers", get(props, prefix + "bootstrapServers", "localhost:9092"));
        // Recommended
        kp.put("acks", get(props, prefix + "acks", "all"));
        kp.put("enable.idempotence", get(props, prefix + "enableIdempotence", "true"));
        kp.put("compression.type", get(props, prefix + "compression.type", "snappy"));
        // Serializers: we send byte[] (leave key null)
        kp.put("key.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
        kp.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");

        // Optional tunings (copy if present)
        copyIfPresent(props, prefix + "linger.ms", kp, "linger.ms");
        copyIfPresent(props, prefix + "batch.size", kp, "batch.size");
        copyIfPresent(props, prefix + "max.in.flight.requests.per.connection", kp, "max.in.flight.requests.per.connection");
        copyIfPresent(props, prefix + "retries", kp, "retries");
        copyIfPresent(props, prefix + "delivery.timeout.ms", kp, "delivery.timeout.ms");
        copyIfPresent(props, prefix + "request.timeout.ms", kp, "request.timeout.ms");

        // Instantiate the real Kafka client (you must have KafkaExternalClient in the same package)
        return new KafkaExternalClient(alias, kp);
    }

    private static String get(Properties p, String key, String def) {
        String v = p.getProperty(key);
        return v == null ? def : v.trim();
    }

    private static void copyIfPresent(Properties src, String srcKey, Properties dst, String dstKey) {
        String v = src.getProperty(srcKey);
        if (v != null && !v.isBlank()) dst.put(dstKey, v.trim());
    }

    /* =========================
     * Built-in Logging client
     * ========================= */

    /**
     * A minimal client that just logs. Good for MVP and tests.
     * Replace with real implementations ("http", "rabbitmq", etc.) later.
     */
    public static final class LoggingClient implements IExternalSystemClient {
        private final String alias;

        public LoggingClient(String alias) {
            this.alias = alias;
        }

        @Override
        public String systemAlias() {
            return alias;
        }

        @Override
        public java.util.concurrent.CompletionStage<Void> send(String destination, String payload) {
            System.out.println("[ExternalClientFactory.LoggingClient] alias=" + alias
                    + ", destination=" + destination
                    + ", payload=" + (payload == null ? "null" : truncate(payload, 256)));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletionStage<Void> send(String destination, String payload, Map<String, String> headers) {
            System.out.println("[ExternalClientFactory.LoggingClient] alias=" + alias
                    + ", destination=" + destination
                    + ", headers=" + headers
                    + ", payload=" + (payload == null ? "null" : truncate(payload, 256)));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void close() {
            // nothing to close
        }

        private static String truncate(String s, int max) {
            if (s == null) return null;
            return s.length() <= max ? s : s.substring(0, max) + "...";
        }
    }
}
