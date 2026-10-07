package com.gametrust.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "livekit")
public class LiveKitProperties {

    private String serverUrl;
    private String apiKey;
    private String apiSecret;
    private long tokenTtlSeconds = 300;

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiSecret() {
        return apiSecret;
    }

    public void setApiSecret(String apiSecret) {
        this.apiSecret = apiSecret;
    }

    public long getTokenTtlSeconds() {
        return tokenTtlSeconds;
    }

    public void setTokenTtlSeconds(long tokenTtlSeconds) {
        this.tokenTtlSeconds = tokenTtlSeconds;
    }
}
