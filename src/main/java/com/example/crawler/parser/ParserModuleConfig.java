package com.example.crawler.parser;

import com.example.crawler.shared.KafkaCredentials;
import com.example.crawler.shared.RawPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.kafka.support.serializer.JsonDeserializer;

/**
 * Configuration for the parser module.
 */
@Configuration
@EnableKafka
@EnableConfigurationProperties(KafkaCredentials.class)
class ParserModuleConfig {

    private static final Logger log = LoggerFactory.getLogger(ParserModuleConfig.class);

    /**
     * Creates a consumer factory that uses reader credentials for consuming.
     */
    @Bean
    ConsumerFactory<String, RawPage> rawPageConsumerFactory(
            KafkaProperties kafkaProperties, KafkaCredentials kafkaCredentials) {
        var props = kafkaProperties.buildConsumerProperties(null);
        // Override with reader credentials if configured
        var reader = kafkaCredentials.reader();
        if (reader != null && reader.username() != null && !reader.username().isEmpty()) {
            String jaasConfig = kafkaCredentials.toJaasConfig(reader);
            props.put("sasl.jaas.config", jaasConfig);
            log.info("Parser ConsumerFactory configured with reader credentials: {}", reader.username());
        } else {
            log.warn("Parser ConsumerFactory: no reader credentials configured, using defaults");
        }
        // Configure JsonDeserializer for RawPage
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.example.crawler.shared");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, RawPage.class.getName());
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, RawPage> rawPageListenerContainerFactory(
            ConsumerFactory<String, RawPage> rawPageConsumerFactory) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, RawPage>();
        factory.setConsumerFactory(rawPageConsumerFactory);
        // Commit offset only after successful processing (not on error)
        factory.getContainerProperties().setAckMode(AckMode.RECORD);
        return factory;
    }
}
