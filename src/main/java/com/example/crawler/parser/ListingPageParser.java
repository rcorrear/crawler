package com.example.crawler.parser;

import com.example.crawler.shared.AttorneyProfile;
import com.example.crawler.shared.CrawlUrl;
import com.example.crawler.shared.OfficeLocation;
import com.example.crawler.shared.PageType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Parser for the listing page (/attorneys/).
 * Extracts attorney data from the embedded JSON blob in drupalSettings.
 */
@Component
public class ListingPageParser {

    private static final Logger log = LoggerFactory.getLogger(ListingPageParser.class);
    private static final String BASE_URL = "https://www.forthepeople.com";

    private final ObjectMapper objectMapper;

    public ListingPageParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Parse result containing listing-level profiles and detail page URLs.
     */
    public record ParseResult(List<AttorneyProfile> profiles, List<CrawlUrl> detailUrls) {}

    /**
     * Parses the listing page HTML and extracts attorney profiles and detail URLs.
     *
     * @param html the HTML content of the listing page
     * @param source the source identifier
     * @param maxDetailPages maximum number of detail page URLs to produce
     * @return ParseResult containing profiles and URLs to crawl
     */
    public ParseResult parse(String html, String source, int maxDetailPages) {
        List<AttorneyProfile> profiles = new ArrayList<>();
        List<CrawlUrl> detailUrls = new ArrayList<>();

        Document doc = Jsoup.parse(html);

        // Find the embedded JSON in drupalSettings
        Element scriptTag = doc.selectFirst("script[data-drupal-selector=drupal-settings-json]");
        if (scriptTag == null) {
            log.warn("No drupal-settings-json found in listing page");
            return new ParseResult(profiles, detailUrls);
        }

        try {
            String jsonText = scriptTag.data();
            JsonNode root = objectMapper.readTree(jsonText);

            // Navigate to attorneys_master_list.data
            JsonNode attorneysNode = root.path("attorneys_master_list").path("data");
            if (!attorneysNode.isArray()) {
                log.warn("attorneys_master_list.data is not an array");
                return new ParseResult(profiles, detailUrls);
            }

            Instant now = Instant.now();
            int detailCount = 0;

            for (JsonNode attorneyNode : attorneysNode) {
                // Extract listing-level profile
                AttorneyProfile profile = extractProfile(attorneyNode, source, now);
                profiles.add(profile);

                // Produce detail URL if within limit
                if (detailCount < maxDetailPages) {
                    String relativeUrl = attorneyNode.path("url").asText("");
                    if (!relativeUrl.isEmpty()) {
                        String absoluteUrl = relativeUrl.startsWith("/")
                                ? BASE_URL + relativeUrl
                                : relativeUrl;
                        detailUrls.add(new CrawlUrl(absoluteUrl, source, 1, PageType.DETAIL));
                        detailCount++;
                    }
                }
            }

            log.info("Parsed {} attorneys from listing, produced {} detail URLs", profiles.size(), detailCount);

        } catch (Exception e) {
            log.error("Failed to parse listing page JSON: {}", e.getMessage(), e);
        }

        return new ParseResult(profiles, detailUrls);
    }

    private AttorneyProfile extractProfile(JsonNode node, String source, Instant now) {
        String relativeUrl = node.path("url").asText("");
        String absoluteUrl = relativeUrl.startsWith("/") ? BASE_URL + relativeUrl : relativeUrl;

        String name = node.path("name").asText("");
        String surName = node.path("surName").asText("");
        String photo = node.path("photo").asText("");

        // Extract office locations
        List<OfficeLocation> officeLocations = new ArrayList<>();
        JsonNode locationsNode = node.path("officeLocations");
        if (locationsNode.isArray()) {
            for (JsonNode loc : locationsNode) {
                officeLocations.add(new OfficeLocation(
                        loc.path("State").asText(""),
                        loc.path("City").asText(""),
                        loc.path("Abbreviation").asText("")));
            }
        }

        // Extract practice areas
        List<String> practiceAreas = new ArrayList<>();
        JsonNode practiceAreasNode = node.path("practiceAreas");
        if (practiceAreasNode.isArray()) {
            for (JsonNode pa : practiceAreasNode) {
                practiceAreas.add(pa.asText());
            }
        }

        return new AttorneyProfile(
                absoluteUrl,
                name,
                surName,
                null, // title - not available in listing
                null, // location string - will derive from officeLocations
                null, // phone
                null, // email
                photo,
                null, // bio - not available in listing
                practiceAreas,
                List.of(), // education - not available in listing
                List.of(), // barAdmissions - not available in listing
                List.of(), // memberships - not available in listing
                Map.of(), // socialLinks - not available in listing
                officeLocations,
                source,
                now);
    }
}
