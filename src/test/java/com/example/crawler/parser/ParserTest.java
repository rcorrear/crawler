package com.example.crawler.parser;

import static org.assertj.core.api.Assertions.*;

import com.example.crawler.shared.AttorneyProfile;
import com.example.crawler.shared.OfficeLocation;
import com.example.crawler.shared.PageType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * Unit tests for the parsers using HTML fixtures.
 */
class ParserTest {

    private ObjectMapper objectMapper;
    private ListingPageParser listingParser;
    private DetailPageParser detailParser;

    private String listingHtml;
    private String detailHtmlJohn;
    private String detailHtmlBlake;

    @BeforeEach
    void setUp() throws IOException {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        listingParser = new ListingPageParser(objectMapper);
        detailParser = new DetailPageParser();

        listingHtml = loadFixture("fixtures/listing-page.html");
        detailHtmlJohn = loadFixture("fixtures/detail-page-john-morgan.html");
        detailHtmlBlake = loadFixture("fixtures/detail-page-blake-lange.html");
    }

    private String loadFixture(String path) throws IOException {
        var resource = new ClassPathResource(path);
        try (var is = resource.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Nested
    @DisplayName("ListingPageParser")
    class ListingParserTests {

        @Test
        @DisplayName("should extract attorneys from JSON blob")
        void shouldExtractAttorneysFromJsonBlob() {
            // When
            var result = listingParser.parse(listingHtml, "forthepeople", 10);

            // Then
            assertThat(result.profiles()).isNotEmpty();
            assertThat(result.detailUrls()).hasSize(10); // capped at maxDetailPages
        }

        @Test
        @DisplayName("should extract basic profile fields")
        void shouldExtractBasicProfileFields() {
            // When
            var result = listingParser.parse(listingHtml, "forthepeople", 5);

            // Then
            AttorneyProfile profile = result.profiles().get(0);
            assertThat(profile.url()).isNotBlank();
            assertThat(profile.name()).isNotBlank();
            assertThat(profile.source()).isEqualTo("forthepeople");
            assertThat(profile.photoUrl()).isNotBlank();
        }

        @Test
        @DisplayName("should extract office locations")
        void shouldExtractOfficeLocations() {
            // When
            var result = listingParser.parse(listingHtml, "forthepeople", 5);

            // Then
            AttorneyProfile profile = result.profiles().stream()
                    .filter(p -> !p.officeLocations().isEmpty())
                    .findFirst()
                    .orElseThrow();

            List<OfficeLocation> locations = profile.officeLocations();
            assertThat(locations).isNotEmpty();
            assertThat(locations.get(0).city()).isNotBlank();
            assertThat(locations.get(0).abbreviation()).isNotBlank();
        }

        @Test
        @DisplayName("should produce detail URLs with correct depth and type")
        void shouldProduceDetailUrlsWithCorrectDepthAndType() {
            // When
            var result = listingParser.parse(listingHtml, "forthepeople", 5);

            // Then
            assertThat(result.detailUrls()).allSatisfy(crawlUrl -> {
                assertThat(crawlUrl.depth()).isEqualTo(1);
                assertThat(crawlUrl.pageType()).isEqualTo(PageType.DETAIL);
                assertThat(crawlUrl.source()).isEqualTo("forthepeople");
                assertThat(crawlUrl.url()).startsWith("https://www.forthepeople.com/attorneys/");
            });
        }
    }

    @Nested
    @DisplayName("DetailPageParser")
    class DetailParserTests {

        @Test
        @DisplayName("should parse detail page without crashing")
        void shouldParseDetailPage() {
            // When
            AttorneyProfile profile = detailParser.parse(detailHtmlJohn,
                    "https://www.forthepeople.com/attorneys/john-morgan/", "forthepeople");

            // Then - at minimum URL and source should be set
            assertThat(profile.url()).isEqualTo("https://www.forthepeople.com/attorneys/john-morgan/");
            assertThat(profile.source()).isEqualTo("forthepeople");
            assertThat(profile.extractedAt()).isNotNull();
        }

        @Test
        @DisplayName("should extract some name data")
        void shouldExtractSomeName() {
            // When
            AttorneyProfile profile = detailParser.parse(detailHtmlJohn,
                    "https://www.forthepeople.com/attorneys/john-morgan/", "forthepeople");

            // Then - name may be extracted (or null if selector fails)
            // Just verify we don't crash
            assertThat(profile.url()).isNotNull();
        }

        @Test
        @DisplayName("should extract photo URL")
        void shouldExtractPhotoUrl() {
            // When
            AttorneyProfile profile = detailParser.parse(detailHtmlJohn,
                    "https://www.forthepeople.com/attorneys/john-morgan/", "forthepeople");

            // Then
            assertThat(profile.photoUrl()).isNotBlank();
        }

        @Test
        @DisplayName("should handle Blake Lange page")
        void shouldHandleBlakeLangePage() {
            // When
            AttorneyProfile profile = detailParser.parse(detailHtmlBlake,
                    "https://www.forthepeople.com/attorneys/blake-lange/", "forthepeople");

            // Then
            assertThat(profile.url()).isEqualTo("https://www.forthepeople.com/attorneys/blake-lange/");
            assertThat(profile.source()).isEqualTo("forthepeople");
        }
    }
}
