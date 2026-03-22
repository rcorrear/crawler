package com.example.crawler.shared;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/**
 * Shared Kafka configuration for producer with writer credentials.
 */
@Configuration
@EnableConfigurationProperties(KafkaCredentials.class)
public class SharedKafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(SharedKafkaConfig.class);

    /**
     * Creates a producer factory that uses writer credentials for producing.
     */
    @Bean
    ProducerFactory<String, Object> producerFactory(
            KafkaProperties kafkaProperties, KafkaCredentials kafkaCredentials) {
        var props = kafkaProperties.buildProducerProperties(null);
        // Override with writer credentials if configured
        var writer = kafkaCredentials.writer();
        if (writer != null && writer.username() != null && !writer.username().isEmpty()) {
            String jaasConfig = kafkaCredentials.toJaasConfig(writer);
            props.put("sasl.jaas.config", jaasConfig);
            log.info("ProducerFactory configured with writer credentials: {}", writer.username());
        } else {
            log.warn("ProducerFactory: no writer credentials configured, using defaults");
        }
        return new DefaultKafkaProducerFactory<>(props);
    }

    /**
     * Generic KafkaTemplate for any message type.
     */
    @Bean
    KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    /**
     * Typed KafkaTemplate for CrawlUrl messages.
     */
    @Bean
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, CrawlUrl> crawlUrlKafkaTemplate(ProducerFactory<String, ?> producerFactory) {
        return new KafkaTemplate<>((ProducerFactory<String, CrawlUrl>) producerFactory);
    }

    /**
     * Typed KafkaTemplate for RawPage messages.
     */
    @Bean
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, RawPage> rawPageKafkaTemplate(ProducerFactory<String, ?> producerFactory) {
        return new KafkaTemplate<>((ProducerFactory<String, RawPage>) producerFactory);
    }

    /**
     * Typed KafkaTemplate for AttorneyProfile messages.
     */
    @Bean
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, AttorneyProfile> attorneyProfileKafkaTemplate(
            ProducerFactory<String, ?> producerFactory) {
        return new KafkaTemplate<>((ProducerFactory<String, AttorneyProfile>) producerFactory);
    }

    /**
     * Typed KafkaTemplate for CrawlError messages.
     */
    @Bean
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, CrawlError> crawlErrorKafkaTemplate(ProducerFactory<String, ?> producerFactory) {
        return new KafkaTemplate<>((ProducerFactory<String, CrawlError>) producerFactory);
    }
}
