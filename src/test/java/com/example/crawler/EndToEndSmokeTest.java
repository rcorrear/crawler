package com.example.crawler;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.crawler.fetcher.FetchException;
import com.example.crawler.fetcher.FetcherService;
import com.example.crawler.shared.Topics;
import com.example.crawler.storage.AttorneyProfileRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * End-to-end smoke test.
 * Uses embedded Kafka, H2 database, and mocked fetcher.
 */
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 1,
        topics = {
            Topics.URLS_TO_CRAWL,
            Topics.RAW_PAGES,
            Topics.PARSED_ITEMS,
            Topics.ERRORS
        },
        brokerProperties = {"listeners=PLAINTEXT://localhost:0", "port=0"})
@DirtiesContext
class EndToEndSmokeTest {

    @Autowired
    private AttorneyProfileRepository repository;

    @MockitoBean
    private FetcherService fetcherService;

    private String listingHtml;

    @BeforeEach
    void setUp() throws IOException, FetchException {
        // Load listing page fixture
        listingHtml = loadFixture("fixtures/listing-page.html");

        // Mock fetcher to return real listing HTML
        when(fetcherService.fetch("https://www.forthepeople.com/attorneys/"))
                .thenReturn(new FetcherService.FetchResult(200, listingHtml));
    }

    private String loadFixture(String path) throws IOException {
        var resource = new ClassPathResource(path);
        try (var is = resource.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void fullPipelineShouldStoreAttorneyProfiles() {
        // When: the application starts, scheduler seeds the listing URL
        // (Scheduler runs automatically via ApplicationRunner on startup)

        // Then: wait for profiles to appear in the database
        await().atMost(Duration.ofMinutes(2)).pollInterval(Duration.ofSeconds(2)).untilAsserted(() -> {
            long count = repository.count();
            // Listing profiles should be stored (extracted from JSON blob)
            assertThat(count).isPositive();
        });
    }

    private static org.awaitility.core.ConditionFactory await() {
        return org.awaitility.Awaitility.await();
    }
}
