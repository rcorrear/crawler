package com.example.crawler.shared;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.lang.Nullable;

/**
 * Kafka credentials configuration for separate reader/writer users.
 */
@ConfigurationProperties(prefix = "crawler.kafka")
public record KafkaCredentials(
        @Nullable Credentials writer,
        @Nullable Credentials reader) {

    public record Credentials(String username, String password) {}

    /**
     * Builds the JAAS config string for these credentials.
     */
    public String toJaasConfig(Credentials creds) {
        if (creds == null || creds.username() == null || creds.username().isEmpty()) {
            return "";
        }
        return "org.apache.kafka.common.security.scram.ScramLoginModule required " +
               "username=\"%s\" password=\"%s\";".formatted(creds.username(), creds.password());
    }
}
