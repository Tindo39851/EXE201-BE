package com.gametrust.backend.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LiveKitPropertiesTest {

    @Test
    void allowsInsecureWebSocketOnlyForLoopbackDevelopment() {
        LiveKitProperties properties = configured("ws://localhost:7880", "devkey", "secret");

        assertEquals("http://localhost:7880", properties.getHttpApiUrl());
    }

    @Test
    void rejectsInsecureRemoteWebSocket() {
        LiveKitProperties properties = configured("ws://voice.example.com", "key", "strong-secret");

        assertThrows(IllegalStateException.class, properties::validate);
    }

    @Test
    void requiresSecureProductionCredentials() {
        LiveKitProperties properties = configured("wss://voice.example.com", "devkey", "secret");

        assertThrows(IllegalStateException.class, properties::validate);
    }

    @Test
    void convertsSecureWebSocketToHttpsApiUrl() {
        LiveKitProperties properties = configured("wss://voice.example.com", "key", "strong-secret");

        assertEquals("https://voice.example.com", properties.getHttpApiUrl());
    }

    private LiveKitProperties configured(String url, String key, String secret) {
        LiveKitProperties properties = new LiveKitProperties();
        properties.setServerUrl(url);
        properties.setApiKey(key);
        properties.setApiSecret(secret);
        properties.setTokenTtlSeconds(300);
        return properties;
    }
}
