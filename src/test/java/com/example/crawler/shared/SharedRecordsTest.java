package com.example.crawler.shared;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for shared message records serialization.
 */
class SharedRecordsTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Nested
    @DisplayName("CrawlUrl serialization")
    class CrawlUrlTest {

        @Test
        @DisplayName("should serialize and deserialize correctly")
        void roundTrip() throws Exception {
            CrawlUrl original = new CrawlUrl(
                    "https://example.com/page",
                    "forthepeople",
                    0,
                    PageType.LISTING);

            String json = objectMapper.writeValueAsString(original);
            CrawlUrl deserialized = objectMapper.readValue(json, CrawlUrl.class);

            assertEquals(original, deserialized);
            assertEquals("https://example.com/page", deserialized.url());
            assertEquals("forthepeople", deserialized.source());
            assertEquals(0, deserialized.depth());
            assertEquals(PageType.LISTING, deserialized.pageType());
        }
    }

    @Nested
    @DisplayName("RawPage serialization")
    class RawPageTest {

        @Test
        @DisplayName("should serialize and deserialize correctly")
        void roundTrip() throws Exception {
            Instant now = Instant.now();
            RawPage original = new RawPage(
                    "https://example.com/page",
                    200,
                    "<html><body>content</body></html>",
                    "forthepeople",
                    0,
                    PageType.LISTING,
                    now);

            String json = objectMapper.writeValueAsString(original);
            RawPage deserialized = objectMapper.readValue(json, RawPage.class);

            assertEquals(original.url(), deserialized.url());
            assertEquals(200, deserialized.status());
            assertEquals(original.html(), deserialized.html());
            assertEquals(original.source(), deserialized.source());
            assertEquals(original.depth(), deserialized.depth());
            assertEquals(PageType.LISTING, deserialized.pageType());
            assertEquals(original.fetchedAt(), deserialized.fetchedAt());
        }
    }

    @Nested
    @DisplayName("AttorneyProfile serialization")
    class AttorneyProfileTest {

        @Test
        @DisplayName("should serialize and deserialize correctly")
        void roundTrip() throws Exception {
            Instant now = Instant.now();
            AttorneyProfile original = new AttorneyProfile(
                    "https://example.com/attorneys/john-doe",
                    "John Doe",
                    "Doe",
                    "TRIAL ATTORNEY",
                    "New York, NY",
                    "+1234567890",
                    "john@example.com",
                    "https://example.com/photo.jpg",
                    "Experienced attorney",
                    List.of("Personal Injury", "Car Accident"),
                    List.of("Harvard Law School"),
                    List.of("New York Bar"),
                    List.of("ABA"),
                    Map.of("linkedin", "https://linkedin.com/in/johndoe"),
                    List.of(new OfficeLocation("New York", "New York", "NY")),
                    "forthepeople",
                    now);

            String json = objectMapper.writeValueAsString(original);
            AttorneyProfile deserialized = objectMapper.readValue(json, AttorneyProfile.class);

            assertEquals(original.url(), deserialized.url());
            assertEquals(original.name(), deserialized.name());
            assertEquals(original.surname(), deserialized.surname());
            assertEquals(original.title(), deserialized.title());
            assertEquals(original.location(), deserialized.location());
            assertEquals(original.phone(), deserialized.phone());
            assertEquals(original.email(), deserialized.email());
            assertEquals(original.photoUrl(), deserialized.photoUrl());
            assertEquals(original.bio(), deserialized.bio());
            assertEquals(original.practiceAreas(), deserialized.practiceAreas());
            assertEquals(original.education(), deserialized.education());
            assertEquals(original.barAdmissions(), deserialized.barAdmissions());
            assertEquals(original.memberships(), deserialized.memberships());
            assertEquals(original.socialLinks(), deserialized.socialLinks());
            assertEquals(original.officeLocations().size(), deserialized.officeLocations().size());
            assertEquals(original.source(), deserialized.source());
        }

        @Test
        @DisplayName("should handle null collections with defaults")
        void nullCollectionsBecomeEmpty() {
            AttorneyProfile profile = new AttorneyProfile(
                    "https://example.com/attorneys/john-doe",
                    "John Doe",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "forthepeople",
                    Instant.now());

            assertNotNull(profile.practiceAreas());
            assertNotNull(profile.education());
            assertNotNull(profile.barAdmissions());
            assertNotNull(profile.memberships());
            assertNotNull(profile.socialLinks());
            assertNotNull(profile.officeLocations());
            assertTrue(profile.practiceAreas().isEmpty());
            assertTrue(profile.education().isEmpty());
            assertTrue(profile.barAdmissions().isEmpty());
            assertTrue(profile.memberships().isEmpty());
            assertTrue(profile.socialLinks().isEmpty());
            assertTrue(profile.officeLocations().isEmpty());
        }
    }

    @Nested
    @DisplayName("OfficeLocation serialization")
    class OfficeLocationTest {

        @Test
        @DisplayName("should serialize and deserialize correctly")
        void roundTrip() throws Exception {
            OfficeLocation original = new OfficeLocation("New York", "New York", "NY");

            String json = objectMapper.writeValueAsString(original);
            OfficeLocation deserialized = objectMapper.readValue(json, OfficeLocation.class);

            assertEquals(original, deserialized);
        }
    }

    @Nested
    @DisplayName("CrawlError serialization")
    class CrawlErrorTest {

        @Test
        @DisplayName("should serialize and deserialize correctly")
        void roundTrip() throws Exception {
            Instant now = Instant.now();
            CrawlError original = new CrawlError(
                    "https://example.com/bad-url",
                    "fetcher",
                    "Connection timeout",
                    now);

            String json = objectMapper.writeValueAsString(original);
            CrawlError deserialized = objectMapper.readValue(json, CrawlError.class);

            assertEquals(original.url(), deserialized.url());
            assertEquals("fetcher", deserialized.stage());
            assertEquals("Connection timeout", deserialized.error());
            assertEquals(original.occurredAt(), deserialized.occurredAt());
        }
    }

    @Nested
    @DisplayName("PageType serialization")
    class PageTypeTest {

        @Test
        @DisplayName("should serialize and deserialize LISTING")
        void listingRoundTrip() throws Exception {
            String json = objectMapper.writeValueAsString(PageType.LISTING);
            PageType deserialized = objectMapper.readValue(json, PageType.class);
            assertEquals(PageType.LISTING, deserialized);
        }

        @Test
        @DisplayName("should serialize and deserialize DETAIL")
        void detailRoundTrip() throws Exception {
            String json = objectMapper.writeValueAsString(PageType.DETAIL);
            PageType deserialized = objectMapper.readValue(json, PageType.class);
            assertEquals(PageType.DETAIL, deserialized);
        }
    }
}
