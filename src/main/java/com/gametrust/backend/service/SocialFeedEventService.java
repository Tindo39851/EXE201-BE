package com.gametrust.backend.service;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Broadcasts lightweight invalidation events to browsers viewing the public social feed. */
@Component
public class SocialFeedEventService {
    private final Map<String, SseEmitter> clients = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "social-feed-sse-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    public SocialFeedEventService() {
        heartbeat.scheduleAtFixedRate(this::sendHeartbeats, 20, 20, TimeUnit.SECONDS);
    }

    public SseEmitter subscribe() {
        String clientId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(0L);
        clients.put(clientId, emitter);
        emitter.onCompletion(() -> clients.remove(clientId));
        emitter.onTimeout(() -> clients.remove(clientId));
        emitter.onError(error -> clients.remove(clientId));
        try {
            emitter.send(SseEmitter.event().name("ready").data(Map.of("connected", true)));
        } catch (IOException error) {
            clients.remove(clientId);
            emitter.completeWithError(error);
        }
        return emitter;
    }

    public void publish(String type, String postId) {
        Map<String, String> event = Map.of("type", type, "postId", postId);
        clients.forEach((clientId, emitter) -> {
            try {
                emitter.send(SseEmitter.event().name("social-update").data(event));
            } catch (IOException | IllegalStateException error) {
                clients.remove(clientId, emitter);
                emitter.completeWithError(error);
            }
        });
    }

    private void sendHeartbeats() {
        clients.forEach((clientId, emitter) -> {
            try {
                emitter.send(SseEmitter.event().comment("keepalive"));
            } catch (IOException | IllegalStateException error) {
                clients.remove(clientId, emitter);
                emitter.completeWithError(error);
            }
        });
    }

    @PreDestroy
    void shutdown() {
        heartbeat.shutdownNow();
        clients.values().forEach(SseEmitter::complete);
        clients.clear();
    }
}
