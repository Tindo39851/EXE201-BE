package com.gametrust.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootApplication
public class GameTrustApplication {

    public static void main(String[] args) {
        loadDotEnv();
        SpringApplication.run(GameTrustApplication.class, args);
    }

    private static void loadDotEnv() {
        Path envPath = Path.of(".env");
        if (!Files.exists(envPath)) {
            // Check parent directory if run from subfolder
            envPath = Path.of("../.env");
        }
        if (Files.exists(envPath)) {
            try (var lines = Files.lines(envPath)) {
                lines.filter(l -> !l.isBlank() && !l.startsWith("#") && l.contains("="))
                     .forEach(line -> {
                         int idx = line.indexOf('=');
                         String key = line.substring(0, idx).trim();
                         String value = line.substring(idx + 1).trim();
                         if (System.getProperty(key) == null && System.getenv(key) == null) {
                             System.setProperty(key, value);
                         }
                     });
            } catch (Exception ignored) {
            }
        }
    }
}
