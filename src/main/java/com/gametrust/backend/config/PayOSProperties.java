package com.gametrust.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "payos")
public class PayOSProperties {

    private String clientId = "5ba68e28-a12c-4333-bb84-334f06b24223";
    private String apiKey = "ccdaff83-e2fb-40ff-8ed9-498e82319c32";
    private String checksumKey = "cada92968c0542fd09529e0787c5a1574521d611fc9485a991e826b48e552c12";

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getChecksumKey() {
        return checksumKey;
    }

    public void setChecksumKey(String checksumKey) {
        this.checksumKey = checksumKey;
    }
}
