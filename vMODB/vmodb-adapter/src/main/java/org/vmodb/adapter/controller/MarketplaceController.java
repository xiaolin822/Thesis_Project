package org.vmodb.adapter.controller;

import org.vmodb.adapter.client.VmodbClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/marketplace")
public class MarketplaceController {
    private final VmodbClient client;

    public MarketplaceController(VmodbClient client) {
        this.client = client;
    }

    @GetMapping("/items/{id}")
    public Mono<ResponseEntity<Object>> getItem(@PathVariable String id) {
        return client.get("/marketplace/items/" + id, Object.class)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity.status(502).body("vMODB unavailable")));
    }
}
