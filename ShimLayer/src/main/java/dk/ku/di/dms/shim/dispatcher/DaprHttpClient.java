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
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(event.payload()))
                .build();
        System.out.println(event.payload());

        // Both async invocation and its subsequent pipeline execution run within the daprExecutor context
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() == 200 || response.statusCode() == 204) {
                        onSuccess.accept(event.id());
                    } else {
                        System.err.println("[Dapr] Dispatch failed! ID: " + event.id() +
                                " Status: " + response.statusCode() +
                                " Body: " + response.body());
                    }
                })
                .exceptionally(ex -> {
                    System.err.println("[Dapr] Network anomaly! ID: " + event.id() + " Error: " + ex.getMessage());
                    return null;
                });
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