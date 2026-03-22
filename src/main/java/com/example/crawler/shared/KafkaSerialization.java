package com.example.crawler.shared;

import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

/**
 * Configuration for Kafka serialization of shared message types.
 */
public final class KafkaSerialization {

    private KafkaSerialization() {}

    /**
     * The trusted packages for JSON deserialization.
     */
    public static final String TRUSTED_PACKAGES = "com.example.crawler.shared";

    /**
     * Creates a JsonSerializer for the shared message types.
     */
    public static JsonSerializer<Object> jsonSerializer() {
        return new JsonSerializer<>();
    }

    /**
     * Creates a JsonDeserializer for the shared message types.
     */
    public static JsonDeserializer<Object> jsonDeserializer() {
        JsonDeserializer<Object> deserializer = new JsonDeserializer<>();
        deserializer.addTrustedPackages(TRUSTED_PACKAGES);
        return deserializer;
    }
}
