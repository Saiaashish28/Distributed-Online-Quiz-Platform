package com.quizsphere.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        Cors cors,
        Registration registration,
        BootstrapAdmin bootstrapAdmin,
        Quiz quiz,
        RateLimit rateLimit) {

    public record Jwt(String secret, long expirationMinutes) {
    }

    public record Cors(String allowedOrigins) {
        public List<String> originList() {
            if (allowedOrigins == null || allowedOrigins.isBlank()) {
                return List.of();
            }
            return Arrays.stream(allowedOrigins.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
    }

    public record Registration(String adminInviteCode, boolean studentSelfRegistration) {
    }

    public record BootstrapAdmin(String email, String password, String name) {
    }

    public record Quiz(int graceSeconds, long autoSubmitIntervalMs) {
    }

    public record RateLimit(int loginPerMinute, int joinPerMinute) {
    }
}
