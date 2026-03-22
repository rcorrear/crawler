package com.example.crawler.scheduler;

import com.example.crawler.shared.CrawlUrl;
import com.example.crawler.shared.PageType;
import com.example.crawler.shared.SeenUrlService;
import com.example.crawler.shared.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Configuration for the scheduler module.
 */
@Configuration
class SchedulerConfig {

    private static final Logger log = LoggerFactory.getLogger(SchedulerConfig.class);

    private static final String BASE_URL = "https://www.forthepeople.com";
    private static final String LISTING_PATH = "/attorneys/";
    private static final String SOURCE = "forthepeople";

    @Bean
    ApplicationRunner schedulerRunner(
            KafkaTemplate<String, CrawlUrl> kafkaTemplate, SeenUrlService seenUrlService) {
        return args -> {
            String listingUrl = BASE_URL + LISTING_PATH;

            // Check if we've already scheduled this URL
            if (seenUrlService.hasBeenSeen(listingUrl)) {
                log.info("URL already scheduled, skipping: {}", listingUrl);
                return;
            }

            // Mark as seen before producing to prevent duplicates on restart
            if (!seenUrlService.markAsSeenIfNew(listingUrl)) {
                log.info("URL already scheduled (race condition), skipping: {}", listingUrl);
                return;
            }

            CrawlUrl crawlUrl = new CrawlUrl(listingUrl, SOURCE, 0, PageType.LISTING);
            kafkaTemplate.send(Topics.URLS_TO_CRAWL, listingUrl, crawlUrl);

            log.info("Seeded 1 URL to {}: {}", Topics.URLS_TO_CRAWL, listingUrl);
        };
    }
}
