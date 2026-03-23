package com.example.crawler.parser;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.example.crawler.shared.AttorneyProfile;
import com.example.crawler.shared.CrawlUrl;
import com.example.crawler.shared.PageType;
import com.example.crawler.shared.RawPage;
import com.example.crawler.shared.SeenUrlService;
import com.example.crawler.shared.Topics;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Integration test for the parser module.
 */
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 1,
        topics = {Topics.RAW_PAGES, Topics.PARSED_ITEMS, Topics.URLS_TO_CRAWL},
        brokerProperties = {"listeners=PLAINTEXT://localhost:0", "port=0"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ParserIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Autowired
    private KafkaTemplate<String, RawPage> rawPageTemplate;

    @MockitoBean
    private SeenUrlService seenUrlService;

    private String listingHtml;
    private String detailHtml;

    @BeforeEach
    void setUp() throws IOException {
        listingHtml = loadFixture("fixtures/listing-page.html");
        detailHtml = loadFixture("fixtures/detail-page-john-morgan.html");

        // Allow all URL scheduling
        when(seenUrlService.markAsScheduledIfNew(anyString())).thenReturn(true);
    }

    private String loadFixture(String path) throws IOException {
        var resource = new ClassPathResource(path);
        try (var is = resource.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void parserShouldProcessListingPage() throws Exception {
        // Given: consumers for parsed_items and urls_to_crawl
        Consumer<String, AttorneyProfile> profileConsumer = createProfileConsumer();
        profileConsumer.subscribe(List.of(Topics.PARSED_ITEMS));

        Consumer<String, CrawlUrl> urlConsumer = createCrawlUrlConsumer();
        urlConsumer.subscribe(List.of(Topics.URLS_TO_CRAWL));

        // Given: a listing page RawPage
        RawPage rawPage = new RawPage(
                "https://www.forthepeople.com/attorneys/",
                200,
                listingHtml,
                "forthepeople",
                0,
                PageType.LISTING,
                Instant.now());

        // When: we produce the RawPage
        rawPageTemplate.send(Topics.RAW_PAGES, rawPage.url(), rawPage);

        // Then: we should receive multiple AttorneyProfiles
        var profileRecords = KafkaTestUtils.getRecords(profileConsumer, Duration.ofSeconds(30), 10);
        assertThat(profileRecords.count()).isGreaterThanOrEqualTo(10);

        AttorneyProfile profile = StreamSupport.stream(profileRecords.records(Topics.PARSED_ITEMS).spliterator(), false)
                .map(ConsumerRecord::value)
                .findFirst()
                .orElseThrow();

        assertThat(profile.name()).isNotBlank();
        assertThat(profile.source()).isEqualTo("forthepeople");

        // Then: we should receive detail URLs (capped)
        var urlRecords = KafkaTestUtils.getRecords(urlConsumer, Duration.ofSeconds(30), 160);
        assertThat(urlRecords.count()).isLessThanOrEqualTo(160);

        urlConsumer.close();
        profileConsumer.close();
    }

    @Test
    void parserShouldProcessDetailPage() throws Exception {
        // Given: consumer for parsed_items
        Consumer<String, AttorneyProfile> profileConsumer = createProfileConsumer();
        profileConsumer.subscribe(List.of(Topics.PARSED_ITEMS));

        // Given: a detail page RawPage
        RawPage rawPage = new RawPage(
                "https://www.forthepeople.com/attorneys/john-morgan/",
                200,
                detailHtml,
                "forthepeople",
                1,
                PageType.DETAIL,
                Instant.now());

        // When: we produce the RawPage
        rawPageTemplate.send(Topics.RAW_PAGES, rawPage.url(), rawPage);

        // Then: we should receive an AttorneyProfile
        ConsumerRecord<String, AttorneyProfile> record = KafkaTestUtils.getSingleRecord(
                profileConsumer, Topics.PARSED_ITEMS, Duration.ofSeconds(30));

        AttorneyProfile profile = record.value();
        assertThat(profile.url()).isEqualTo("https://www.forthepeople.com/attorneys/john-morgan/");
        assertThat(profile.source()).isEqualTo("forthepeople");

        profileConsumer.close();
    }

    private Consumer<String, AttorneyProfile> createProfileConsumer() {
        Map<String, Object> props =
                KafkaTestUtils.consumerProps("test-group-profile", "true", embeddedKafka);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.example.crawler.shared");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, AttorneyProfile.class.getName());

        return new DefaultKafkaConsumerFactory<String, AttorneyProfile>(props).createConsumer();
    }

    private Consumer<String, CrawlUrl> createCrawlUrlConsumer() {
        Map<String, Object> props =
                KafkaTestUtils.consumerProps("test-group-url", "true", embeddedKafka);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.example.crawler.shared");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, CrawlUrl.class.getName());

        return new DefaultKafkaConsumerFactory<String, CrawlUrl>(props).createConsumer();
    }
}
