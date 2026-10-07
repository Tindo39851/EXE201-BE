package com.gametrust.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;

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

    public void validate() {
        if (isBlank(serverUrl) || isBlank(apiKey) || isBlank(apiSecret)) {
            throw new IllegalStateException("LiveKit server URL, API key and API secret are required");
        }
        if (tokenTtlSeconds < 30 || tokenTtlSeconds > 900) {
            throw new IllegalStateException("LiveKit token TTL must be between 30 and 900 seconds");
        }

        URI uri = parseServerUri();
        String scheme = uri.getScheme();
        boolean secure = "wss".equalsIgnoreCase(scheme);
        boolean localDevelopment = "ws".equalsIgnoreCase(scheme) && isLoopback(uri.getHost());
        if (!secure && !localDevelopment) {
            throw new IllegalStateException("LiveKit must use WSS; insecure WS is allowed only on loopback");
        }
        if (!localDevelopment && ("devkey".equals(apiKey) || "secret".equals(apiSecret))) {
            throw new IllegalStateException("Default LiveKit credentials are allowed only for local development");
        }
    }

    public String getHttpApiUrl() {
        validate();
        URI uri = parseServerUri();
        String httpScheme = "wss".equalsIgnoreCase(uri.getScheme()) ? "https" : "http";
        try {
            return new URI(httpScheme, uri.getUserInfo(), uri.getHost(), uri.getPort(),
                    uri.getPath(), uri.getQuery(), null).toString();
        } catch (URISyntaxException ex) {
            throw new IllegalStateException("Invalid LiveKit server URL", ex);
        }
    }

    private URI parseServerUri() {
        try {
            URI uri = new URI(serverUrl);
            if (uri.getHost() == null) throw new URISyntaxException(serverUrl, "Host is required");
            return uri;
        } catch (URISyntaxException ex) {
            throw new IllegalStateException("Invalid LiveKit server URL", ex);
        }
    }

    private boolean isLoopback(String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
