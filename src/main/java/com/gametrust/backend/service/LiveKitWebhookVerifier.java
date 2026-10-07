package com.gametrust.backend.service;

import com.gametrust.backend.config.LiveKitProperties;
import io.livekit.server.WebhookReceiver;
import livekit.LivekitWebhook;
import org.springframework.stereotype.Component;

@Component
public class LiveKitWebhookVerifier {

    private final LiveKitProperties properties;

    public LiveKitWebhookVerifier(LiveKitProperties properties) {
        this.properties = properties;
    }

    public LivekitWebhook.WebhookEvent decode(String rawBody, String authorizationHeader) {
        WebhookReceiver receiver = new WebhookReceiver(properties.getApiKey(), properties.getApiSecret());
        return receiver.receive(rawBody, authorizationHeader);
    }
}
