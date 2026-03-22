package com.example.crawler.fetcher;

import com.example.crawler.shared.CrawlUrl;
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
import org.springframework.retry.annotation.EnableRetry;

/**
 * Configuration for the fetcher module.
 */
@Configuration
@EnableKafka
@EnableRetry
@EnableConfigurationProperties({FetcherConfig.class, KafkaCredentials.class})
class FetcherModuleConfig {

    private static final Logger log = LoggerFactory.getLogger(FetcherModuleConfig.class);

    /**
     * Creates a consumer factory that uses reader credentials for consuming.
     */
    @Bean
    ConsumerFactory<String, CrawlUrl> crawlUrlConsumerFactory(
            KafkaProperties kafkaProperties, KafkaCredentials kafkaCredentials) {
        var props = kafkaProperties.buildConsumerProperties(null);
        // Override with reader credentials if configured
        var reader = kafkaCredentials.reader();
        if (reader != null && reader.username() != null && !reader.username().isEmpty()) {
            String jaasConfig = kafkaCredentials.toJaasConfig(reader);
            props.put("sasl.jaas.config", jaasConfig);
            log.info("ConsumerFactory configured with reader credentials: {}", reader.username());
        } else {
            log.warn("ConsumerFactory: no reader credentials configured, using defaults");
        }
        // Configure JsonDeserializer for CrawlUrl
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.example.crawler.shared");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, CrawlUrl.class.getName());
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, CrawlUrl> crawlUrlListenerContainerFactory(
            ConsumerFactory<String, CrawlUrl> crawlUrlConsumerFactory) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, CrawlUrl>();
        factory.setConsumerFactory(crawlUrlConsumerFactory);
        // Commit offset only after successful processing (not on error)
        factory.getContainerProperties().setAckMode(AckMode.RECORD);
        return factory;
    }
}
