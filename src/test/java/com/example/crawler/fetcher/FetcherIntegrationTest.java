package com.example.crawler.fetcher;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.crawler.shared.CrawlError;
import com.example.crawler.shared.CrawlUrl;
import com.example.crawler.shared.PageType;
import com.example.crawler.shared.RawPage;
import com.example.crawler.shared.Topics;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
 * Integration test for the fetcher module.
 * Uses HTML fixtures fetched from the real target site.
 */
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 1,
        topics = {Topics.URLS_TO_CRAWL, Topics.RAW_PAGES, Topics.ERRORS},
        brokerProperties = {"listeners=PLAINTEXT://localhost:0", "port=0"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class FetcherIntegrationTest {

    private static final String LISTING_FIXTURE = "fixtures/listing-page.html";
    private static final String DETAIL_FIXTURE_JOHN = "fixtures/detail-page-john-morgan.html";
    private static final String DETAIL_FIXTURE_BLAKE = "fixtures/detail-page-blake-lange.html";

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Autowired
    private KafkaTemplate<String, CrawlUrl> crawlUrlTemplate;

    @MockitoBean
    private FetcherService fetcherService;

    private String listingHtml;
    private String detailHtmlJohn;
    private String detailHtmlBlake;

    @BeforeEach
    void setUp() throws FetchException, IOException {
        // Load HTML fixtures from real site
        listingHtml = loadFixture(LISTING_FIXTURE);
        detailHtmlJohn = loadFixture(DETAIL_FIXTURE_JOHN);
        detailHtmlBlake = loadFixture(DETAIL_FIXTURE_BLAKE);
    }

    private String loadFixture(String path) throws IOException {
        var resource = new ClassPathResource(path);
        try (var is = resource.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void fetcherShouldFetchListingPageAndProduceRawPage() throws Exception {
        // Given: mock returns listing page HTML
        when(fetcherService.fetch("https://www.forthepeople.com/attorneys/"))
                .thenReturn(new FetcherService.FetchResult(200, listingHtml));

        Consumer<String, RawPage> rawPageConsumer = createRawPageConsumer();
        rawPageConsumer.subscribe(List.of(Topics.RAW_PAGES));

        // Given: the listing page URL
        CrawlUrl crawlUrl = new CrawlUrl(
                "https://www.forthepeople.com/attorneys/",
                "forthepeople",
                0,
                PageType.LISTING);

        // When: we produce the URL to urls_to_crawl
        crawlUrlTemplate.send(Topics.URLS_TO_CRAWL, crawlUrl.url(), crawlUrl);

        // Then: we should receive a RawPage with the real listing HTML
        ConsumerRecord<String, RawPage> record = KafkaTestUtils.getSingleRecord(
                rawPageConsumer, Topics.RAW_PAGES, Duration.ofSeconds(30));

        RawPage rawPage = record.value();
        assertThat(rawPage.url()).isEqualTo("https://www.forthepeople.com/attorneys/");
        assertThat(rawPage.status()).isEqualTo(200);
        assertThat(rawPage.html()).contains("attorneys_master_list"); // JSON blob present
        assertThat(rawPage.pageType()).isEqualTo(PageType.LISTING);

        rawPageConsumer.close();
    }

    @Test
    void fetcherShouldFetchDetailPageAndProduceRawPage() throws Exception {
        // Given: mock returns detail page HTML
        when(fetcherService.fetch("https://www.forthepeople.com/attorneys/john-morgan/"))
                .thenReturn(new FetcherService.FetchResult(200, detailHtmlJohn));

        Consumer<String, RawPage> rawPageConsumer = createRawPageConsumer();
        rawPageConsumer.subscribe(List.of(Topics.RAW_PAGES));

        // Given: a detail page URL
        CrawlUrl crawlUrl = new CrawlUrl(
                "https://www.forthepeople.com/attorneys/john-morgan/",
                "forthepeople",
                1,
                PageType.DETAIL);

        // When: we produce the URL to urls_to_crawl
        crawlUrlTemplate.send(Topics.URLS_TO_CRAWL, crawlUrl.url(), crawlUrl);

        // Then: we should receive a RawPage with the real detail HTML
        ConsumerRecord<String, RawPage> record = KafkaTestUtils.getSingleRecord(
                rawPageConsumer, Topics.RAW_PAGES, Duration.ofSeconds(30));

        RawPage rawPage = record.value();
        assertThat(rawPage.url()).isEqualTo("https://www.forthepeople.com/attorneys/john-morgan/");
        assertThat(rawPage.status()).isEqualTo(200);
        assertThat(rawPage.html()).contains("John Morgan"); // Attorney name present
        assertThat(rawPage.pageType()).isEqualTo(PageType.DETAIL);

        rawPageConsumer.close();
    }

    @Test
    void fetcherShouldSkipAlreadyFetchedUrl() throws Exception {
        // Given: mock returns listing page HTML
        when(fetcherService.fetch("https://www.forthepeople.com/attorneys/"))
                .thenReturn(new FetcherService.FetchResult(200, listingHtml));

        Consumer<String, RawPage> rawPageConsumer = createRawPageConsumer();
        rawPageConsumer.subscribe(List.of(Topics.RAW_PAGES));

        // Given: a URL to fetch
        CrawlUrl crawlUrl = new CrawlUrl(
                "https://www.forthepeople.com/attorneys/",
                "forthepeople",
                0,
                PageType.LISTING);

        // When: we produce the same URL twice
        crawlUrlTemplate.send(Topics.URLS_TO_CRAWL, crawlUrl.url(), crawlUrl);
        crawlUrlTemplate.send(Topics.URLS_TO_CRAWL, crawlUrl.url(), crawlUrl);

        // Then: we should receive exactly one RawPage (dedup)
        ConsumerRecord<String, RawPage> record = KafkaTestUtils.getSingleRecord(
                rawPageConsumer, Topics.RAW_PAGES, Duration.ofSeconds(30));
        assertThat(record.value().url()).isEqualTo("https://www.forthepeople.com/attorneys/");

        // Second message should not produce another RawPage
        var records = rawPageConsumer.poll(Duration.ofSeconds(5));
        assertThat(records.count()).isZero();

        // Verify fetcher was only called once
        verify(fetcherService, times(1)).fetch("https://www.forthepeople.com/attorneys/");

        rawPageConsumer.close();
    }

    @Test
    void fetcherShouldProduceErrorForInvalidUrl() throws Exception {
        // Given: stub for scheduler's listing URL (it runs on startup)
        when(fetcherService.fetch("https://www.forthepeople.com/attorneys/"))
                .thenReturn(new FetcherService.FetchResult(200, listingHtml));

        // Given: stub for invalid URL that throws exception
        when(fetcherService.fetch("https://nonexistent.invalid.test/page"))
                .thenThrow(new FetchException(
                        "https://nonexistent.invalid.test/page",
                        "Connection failed",
                        null));

        // Given: a consumer for errors
        Consumer<String, CrawlError> errorConsumer = createErrorConsumer();
        errorConsumer.subscribe(List.of(Topics.ERRORS));

        // Given: an invalid URL
        CrawlUrl crawlUrl = new CrawlUrl(
                "https://nonexistent.invalid.test/page",
                "forthepeople",
                0,
                PageType.DETAIL);

        // When: we produce the URL to urls_to_crawl
        crawlUrlTemplate.send(Topics.URLS_TO_CRAWL, crawlUrl.url(), crawlUrl);

        // Then: we should receive a CrawlError for the invalid URL
        // Note: scheduler may have produced listing URL first, so poll multiple records
        var records = KafkaTestUtils.getRecords(errorConsumer, Duration.ofSeconds(30), 2);

        CrawlError error = StreamSupport.stream(records.records(Topics.ERRORS).spliterator(), false)
                .map(ConsumerRecord::value)
                .filter(e -> e.url().equals("https://nonexistent.invalid.test/page"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected error for invalid URL not found"));

        assertThat(error.stage()).isEqualTo("fetcher");

        errorConsumer.close();
    }

    private Consumer<String, RawPage> createRawPageConsumer() {
        Map<String, Object> props =
                KafkaTestUtils.consumerProps("test-group-raw", "true", embeddedKafka);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.example.crawler.shared");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, RawPage.class.getName());

        return new DefaultKafkaConsumerFactory<String, RawPage>(props).createConsumer();
    }

    private Consumer<String, CrawlError> createErrorConsumer() {
        Map<String, Object> props =
                KafkaTestUtils.consumerProps("test-group-error", "true", embeddedKafka);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.example.crawler.shared");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, CrawlError.class.getName());

        return new DefaultKafkaConsumerFactory<String, CrawlError>(props).createConsumer();
    }
}
