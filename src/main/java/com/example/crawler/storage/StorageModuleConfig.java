package com.example.crawler.storage;

import com.example.crawler.shared.AttorneyProfile;
import com.example.crawler.shared.KafkaCredentials;
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
 * Configuration for the storage module.
 */
@Configuration
@EnableKafka
@EnableConfigurationProperties(KafkaCredentials.class)
class StorageModuleConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageModuleConfig.class);

    @Bean
    ConsumerFactory<String, AttorneyProfile> attorneyProfileConsumerFactory(
            KafkaProperties kafkaProperties, KafkaCredentials kafkaCredentials) {
        var props = kafkaProperties.buildConsumerProperties(null);
        var reader = kafkaCredentials.reader();
        if (reader != null && reader.username() != null && !reader.username().isEmpty()) {
            String jaasConfig = kafkaCredentials.toJaasConfig(reader);
            props.put("sasl.jaas.config", jaasConfig);
            log.info("Storage ConsumerFactory configured with reader credentials: {}", reader.username());
        } else {
            log.warn("Storage ConsumerFactory: no reader credentials configured, using defaults");
        }
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.example.crawler.shared");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, AttorneyProfile.class.getName());
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, AttorneyProfile> attorneyProfileListenerContainerFactory(
            ConsumerFactory<String, AttorneyProfile> attorneyProfileConsumerFactory) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, AttorneyProfile>();
        factory.setConsumerFactory(attorneyProfileConsumerFactory);
        factory.getContainerProperties().setAckMode(AckMode.RECORD);
        return factory;
    }
}
