package com.example.crawler.scheduler;

import static org.assertj.core.api.Assertions.*;

import com.example.crawler.shared.CrawlUrl;
import com.example.crawler.shared.PageType;
import com.example.crawler.shared.Topics;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration test for the scheduler module.
 */
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 1,
        topics = Topics.URLS_TO_CRAWL,
        brokerProperties = {"listeners=PLAINTEXT://localhost:0", "port=0"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SchedulerIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Test
    void schedulerShouldSeedListingPageUrl() {
        // Given: a consumer for the urls_to_crawl topic
        Consumer<String, CrawlUrl> consumer = createConsumer();
        consumer.subscribe(List.of(Topics.URLS_TO_CRAWL));

        // When: the scheduler runs (it runs on startup via ApplicationRunner)
        // Then: we should receive exactly 1 message
        ConsumerRecord<String, CrawlUrl> record = KafkaTestUtils.getSingleRecord(
                consumer, Topics.URLS_TO_CRAWL, Duration.ofSeconds(10));

        // Verify the message content
        assertThat(record.key()).isEqualTo("https://www.forthepeople.com/attorneys/");

        CrawlUrl crawlUrl = record.value();
        assertThat(crawlUrl.url()).isEqualTo("https://www.forthepeople.com/attorneys/");
        assertThat(crawlUrl.source()).isEqualTo("forthepeople");
        assertThat(crawlUrl.depth()).isEqualTo(0);
        assertThat(crawlUrl.pageType()).isEqualTo(PageType.LISTING);

        consumer.close();
    }

    private Consumer<String, CrawlUrl> createConsumer() {
        Map<String, Object> props =
                KafkaTestUtils.consumerProps("test-group", "true", embeddedKafka);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.example.crawler.shared");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, CrawlUrl.class.getName());

        DefaultKafkaConsumerFactory<String, CrawlUrl> factory =
                new DefaultKafkaConsumerFactory<>(props);
        return factory.createConsumer();
    }
}
