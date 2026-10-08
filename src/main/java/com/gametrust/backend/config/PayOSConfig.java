package com.gametrust.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.payos.PayOS;

@Configuration
public class PayOSConfig {

    private final PayOSProperties properties;

    public PayOSConfig(PayOSProperties properties) {
        this.properties = properties;
    }

    @Bean
    public PayOS payOS() {
        return new PayOS(properties.getClientId(), properties.getApiKey(), properties.getChecksumKey());
    }
}
