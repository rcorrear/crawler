package com.example.crawler.fetcher;

import com.example.crawler.shared.CrawlError;
import com.example.crawler.shared.CrawlUrl;
import com.example.crawler.shared.RawPage;
import com.example.crawler.shared.SeenUrlService;
import com.example.crawler.shared.Topics;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka listener that fetches HTML from URLs.
 */
@Component
class FetcherListener {

    private static final Logger log = LoggerFactory.getLogger(FetcherListener.class);

    private final FetcherService fetcherService;
    private final SeenUrlService seenUrlService;
    private final KafkaTemplate<String, RawPage> rawPageTemplate;
    private final KafkaTemplate<String, CrawlError> errorTemplate;

    FetcherListener(
            FetcherService fetcherService,
            SeenUrlService seenUrlService,
            @Qualifier("rawPageKafkaTemplate") KafkaTemplate<String, RawPage> rawPageTemplate,
            @Qualifier("crawlErrorKafkaTemplate") KafkaTemplate<String, CrawlError> errorTemplate) {
        this.fetcherService = fetcherService;
        this.seenUrlService = seenUrlService;
        this.rawPageTemplate = rawPageTemplate;
        this.errorTemplate = errorTemplate;
    }

    @KafkaListener(
            topics = Topics.URLS_TO_CRAWL,
            groupId = "crawler-fetcher",
            containerFactory = "crawlUrlListenerContainerFactory")
    void onUrl(CrawlUrl crawlUrl) {
        String url = crawlUrl.url();

        // Atomically mark as FETCHED - if already processed, skip
        if (!seenUrlService.markAsFetchedIfNotProcessed(url)) {
            log.info("URL already fetched/processed, skipping: {}", url);
            return;
        }

        try {
            // Fetch the HTML
            var result = fetcherService.fetch(url);

            // Produce RawPage
            // If this fails, we throw and mark as FAILED
            RawPage rawPage = new RawPage(
                    url,
                    result.status(),
                    result.html(),
                    crawlUrl.source(),
                    crawlUrl.depth(),
                    crawlUrl.pageType(),
                    Instant.now());

            var future = rawPageTemplate.send(Topics.RAW_PAGES, url, rawPage);
            future.get(); // Wait for send to complete

            log.info("Produced RawPage for {} ({} bytes)", url, result.html().length());

        } catch (FetchException e) {
            handleFetchError(url, e);
        } catch (Exception e) {
            handleFetchError(url, new FetchException(url, "Failed to produce RawPage: " + e.getMessage(), e));
        }
    }

    private void handleFetchError(String url, FetchException e) {
        // Mark as FAILED to prevent infinite retry
        seenUrlService.markAsFailed(url);

        // Produce error to dead letter queue
        CrawlError error = new CrawlError(url, "fetcher", e.getMessage(), Instant.now());
        try {
            errorTemplate.send(Topics.ERRORS, url, error).get();
        } catch (Exception sendError) {
            log.error("Failed to send error to DLQ for {}: {}", url, sendError.getMessage());
        }
        log.error("Failed to fetch {}: {}", url, e.getMessage());
    }
}
