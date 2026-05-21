package org.vmodb.adapter.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class VmodbClient {
    private final WebClient webClient;

    public VmodbClient(@Value("${vmodb.base-url:http://localhost:3000}") String baseUrl) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    public <T> Mono<T> get(String uri, Class<T> clazz) {
        return webClient.get().uri(uri).retrieve().bodyToMono(clazz);
    }
}
