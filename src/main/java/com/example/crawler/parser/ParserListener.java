package com.example.crawler.parser;

import com.example.crawler.shared.AttorneyProfile;
import com.example.crawler.shared.CrawlError;
import com.example.crawler.shared.CrawlUrl;
import com.example.crawler.shared.PageType;
import com.example.crawler.shared.RawPage;
import com.example.crawler.shared.SeenUrlService;
import com.example.crawler.shared.Topics;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka listener that parses HTML pages and produces attorney profiles and new URLs.
 */
@Component
class ParserListener {

    private static final Logger log = LoggerFactory.getLogger(ParserListener.class);

    private final ListingPageParser listingPageParser;
    private final DetailPageParser detailPageParser;
    private final SeenUrlService seenUrlService;
    private final KafkaTemplate<String, AttorneyProfile> profileTemplate;
    private final KafkaTemplate<String, CrawlUrl> crawlUrlTemplate;
    private final KafkaTemplate<String, CrawlError> errorTemplate;
    private final int maxDetailPages;

    ParserListener(
            ListingPageParser listingPageParser,
            DetailPageParser detailPageParser,
            SeenUrlService seenUrlService,
            @Qualifier("attorneyProfileKafkaTemplate") KafkaTemplate<String, AttorneyProfile> profileTemplate,
            @Qualifier("crawlUrlKafkaTemplate") KafkaTemplate<String, CrawlUrl> crawlUrlTemplate,
            @Qualifier("crawlErrorKafkaTemplate") KafkaTemplate<String, CrawlError> errorTemplate,
            @Value("${crawler.max-detail-pages:160}") int maxDetailPages) {
        this.listingPageParser = listingPageParser;
        this.detailPageParser = detailPageParser;
        this.seenUrlService = seenUrlService;
        this.profileTemplate = profileTemplate;
        this.crawlUrlTemplate = crawlUrlTemplate;
        this.errorTemplate = errorTemplate;
        this.maxDetailPages = maxDetailPages;
    }

    @KafkaListener(
            topics = Topics.RAW_PAGES,
            groupId = "crawler-parser",
            containerFactory = "rawPageListenerContainerFactory")
    void onRawPage(RawPage rawPage) {
        String url = rawPage.url();

        try {
            if (rawPage.pageType() == PageType.LISTING) {
                handleListingPage(rawPage);
            } else if (rawPage.pageType() == PageType.DETAIL) {
                handleDetailPage(rawPage);
            } else {
                log.warn("Unknown page type: {} for URL: {}", rawPage.pageType(), url);
            }
        } catch (Exception e) {
            handleError(url, e);
        }
    }

    private void handleListingPage(RawPage rawPage) throws Exception {
        var result = listingPageParser.parse(rawPage.html(), rawPage.source(), maxDetailPages);

        // Produce all listing-level profiles
        for (AttorneyProfile profile : result.profiles()) {
            profileTemplate.send(Topics.PARSED_ITEMS, profile.url(), profile).get();
        }
        log.info("Produced {} profiles from listing page", result.profiles().size());

        // Produce detail page URLs (with dedup)
        int newUrls = 0;
        for (CrawlUrl crawlUrl : result.detailUrls()) {
            if (seenUrlService.markAsScheduledIfNew(crawlUrl.url())) {
                crawlUrlTemplate.send(Topics.URLS_TO_CRAWL, crawlUrl.url(), crawlUrl).get();
                newUrls++;
            }
        }
        log.info("Produced {} new detail URLs (total discovered: {})", newUrls, result.detailUrls().size());
    }

    private void handleDetailPage(RawPage rawPage) throws Exception {
        AttorneyProfile profile = detailPageParser.parse(rawPage.html(), rawPage.url(), rawPage.source());

        profileTemplate.send(Topics.PARSED_ITEMS, profile.url(), profile).get();
        log.info("Produced profile for {} ({})", profile.name(), rawPage.url());
    }

    private void handleError(String url, Exception e) {
        log.error("Failed to parse {}: {}", url, e.getMessage());
        seenUrlService.markAsFailed(url);

        CrawlError error = new CrawlError(url, "parser", e.getMessage(), Instant.now());
        try {
            errorTemplate.send(Topics.ERRORS, url, error).get();
        } catch (Exception sendError) {
            log.error("Failed to send error to DLQ for {}: {}", url, sendError.getMessage());
        }
    }
}
