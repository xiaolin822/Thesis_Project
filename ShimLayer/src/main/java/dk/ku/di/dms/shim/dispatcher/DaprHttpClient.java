package dk.ku.di.dms.shim.dispatcher;

import dk.ku.di.dms.shim.model.CausalEvent;
import dk.ku.di.dms.shim.model.EventID;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.function.Consumer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.time.Instant;


import com.fasterxml.jackson.databind.ObjectMapper;
import dk.ku.di.dms.shim.model.events.InvoiceIssued;

public class DaprHttpClient {

    // Dedicated bounded thread pool to isolate Dapr I/O from the default common pool
    private final ExecutorService daprExecutor = new ThreadPoolExecutor(
            8,                                  // Core pool size (scale based on allocated HPC vCPUs)
            32,                                 // Maximum pool size
            60L, TimeUnit.SECONDS,              // Keep-alive time for idle threads
            new LinkedBlockingQueue<>(2000),    // Bounded queue to mitigate memory inflation under peak load
            new ThreadFactory() {
                private int count = 0;
                @Override
                public Thread newThread(Runnable r) {
                    // Named threads for predictable profiling and debugging
                    return new Thread(r, "dapr-dispatcher-pool-" + count++);
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy() // Backpressure strategy to handle load spikes without dropping tasks
    );

    // bind the HTTP client to the custom executor
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .executor(daprExecutor)
            .build();

    /**
     * Asynchronously dispatches events to the target Dapr endpoint
     * @param url Dapr Service Invocation URI
     * @param event The causal event payload
     * @param onSuccess Callback triggered upon receiving a successful 20x response
     */
    public void post(String url, CausalEvent event, Consumer<EventID> onSuccess) {

        try {
            ObjectMapper mapper = new ObjectMapper();

            JsonNode root = mapper.readTree(event.payload());

            ObjectNode obj = (ObjectNode) root;

            if (obj.has("issueDate")) {
                long millis = obj.get("issueDate").asLong();

                obj.put(
                        "issueDate",
                        Instant.ofEpochMilli(millis).toString()
                );
            }
            obj.put("invoiceIssued", true);

            if (obj.has("items")) {

                ArrayNode items = (ArrayNode) obj.get("items");

                for (JsonNode item : items) {

                    ObjectNode itemObj = (ObjectNode) item;

                    if (itemObj.has("shipping_limit_date")) {

                        long millis = itemObj.get("shipping_limit_date").asLong();

                        itemObj.put(
                                "shipping_limit_date",
                                Instant.ofEpochMilli(millis).toString()
                        );
                    }
                }
            }

            String fixedJson = mapper.writeValueAsString(obj);
            System.out.println(fixedJson);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(fixedJson))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() == 200 || response.statusCode() == 204) {
                            onSuccess.accept(event.id());
                        } else {
                            System.err.println("[Dapr] Dispatch failed! ID: " + event.id() +
                                    " Status: " + response.statusCode() +
                                    " Body: " + response.body());
                        }
                    });

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * terminates the bounded executor during shutdown phases
     */
    public void shutdown() {
        this.daprExecutor.shutdown();
        try {
            if (!this.daprExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                this.daprExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            this.daprExecutor.shutdownNow();
        }
    }
}