package com.example.crawler.storage;

import static org.assertj.core.api.Assertions.*;

import com.example.crawler.shared.AttorneyProfile;
import com.example.crawler.shared.OfficeLocation;
import com.example.crawler.shared.Topics;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration test for the storage module.
 * Uses H2 in-memory database (from test profile) and embedded Kafka.
 */
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 1,
        topics = Topics.PARSED_ITEMS,
        brokerProperties = {"listeners=PLAINTEXT://localhost:0", "port=0"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class StorageIntegrationTest {

    @Autowired
    private AttorneyProfileRepository repository;

    @Autowired
    private KafkaTemplate<String, AttorneyProfile> profileTemplate;

    @Test
    void shouldStoreListingProfile() {
        // Given: a listing-level profile
        AttorneyProfile listingProfile = new AttorneyProfile(
                "https://www.forthepeople.com/attorneys/john-morgan/",
                "John Morgan",
                "Morgan",
                null, // no title at listing level
                "Orlando, FL",
                null, // no phone
                null, // no email
                "https://example.com/photo.jpg",
                null, // no bio
                List.of("Personal Injury", "Car Accident"),
                List.of(), // no education
                List.of(), // no bar
                List.of(), // no memberships
                Map.of(),
                List.of(new OfficeLocation("Florida", "Orlando", "FL")),
                "forthepeople",
                Instant.now());

        // When: we send it to the storage service
        profileTemplate.send(Topics.PARSED_ITEMS, listingProfile.url(), listingProfile);

        // Then: wait for storage to process
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(repository.existsByUrl("https://www.forthepeople.com/attorneys/john-morgan/")).isTrue();
        });

        AttorneyProfileEntity stored = repository.findByUrl(
                "https://www.forthepeople.com/attorneys/john-morgan/").orElseThrow();

        assertThat(stored.getName()).isEqualTo("John Morgan");
        assertThat(stored.getSurname()).isEqualTo("Morgan");
        assertThat(stored.getTitle()).isNull();
        assertThat(stored.getBio()).isNull();
    }

    @Test
    void shouldUpsertDetailOverListing() {
        // Given: a listing-level profile already stored
        AttorneyProfile listingProfile = new AttorneyProfile(
                "https://www.forthepeople.com/attorneys/blake-lange/",
                "Blake Lange",
                "Lange",
                null,
                null,
                null,
                null,
                "https://example.com/photo.jpg",
                null,
                List.of("Personal Injury"),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                List.of(),
                "forthepeople",
                Instant.now());

        profileTemplate.send(Topics.PARSED_ITEMS, listingProfile.url(), listingProfile);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(repository.existsByUrl("https://www.forthepeople.com/attorneys/blake-lange/")).isTrue();
        });

        // When: a detail-level profile arrives for the same URL
        AttorneyProfile detailProfile = new AttorneyProfile(
                "https://www.forthepeople.com/attorneys/blake-lange/",
                "Blake J. Lange",
                "Lange",
                "TRIAL ATTORNEY",
                "Orlando, FL",
                "+14074201414",
                "blake@forthepeople.com",
                "https://example.com/photo.jpg",
                "Experienced trial attorney specializing in personal injury.",
                List.of("Personal Injury", "Car Accident", "Medical Malpractice"),
                List.of("Harvard Law School", "University of Florida"),
                List.of("Florida Bar"),
                List.of("American Bar Association"),
                Map.of("linkedin", "https://linkedin.com/in/blakelange"),
                List.of(new OfficeLocation("Florida", "Orlando", "FL")),
                "forthepeople",
                Instant.now());

        profileTemplate.send(Topics.PARSED_ITEMS, detailProfile.url(), detailProfile);

        // Then: the existing record is updated (enriched)
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            AttorneyProfileEntity stored = repository.findByUrl(
                    "https://www.forthepeople.com/attorneys/blake-lange/").orElseThrow();

            // Detail enriches listing
            assertThat(stored.getName()).isEqualTo("Blake J. Lange"); // updated
            assertThat(stored.getTitle()).isEqualTo("TRIAL ATTORNEY"); // added
            assertThat(stored.getPhone()).isEqualTo("+14074201414"); // added
            assertThat(stored.getEmail()).isEqualTo("blake@forthepeople.com"); // added
            assertThat(stored.getBio()).contains("trial attorney"); // added
            assertThat(stored.getEducation()).contains("Harvard"); // added
        });

        // Verify no duplicate
        assertThat(repository.count()).isEqualTo(1);
    }

    private static org.awaitility.core.ConditionFactory await() {
        return org.awaitility.Awaitility.await();
    }
}
