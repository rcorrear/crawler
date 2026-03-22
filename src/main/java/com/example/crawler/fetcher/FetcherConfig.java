package com.example.crawler.fetcher;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the fetcher module.
 */
@ConfigurationProperties(prefix = "crawler.fetcher")
public record FetcherConfig(
        int delayMs,
        int timeoutMs,
        String userAgent,
        int maxRetries) {

    /**
     * Default constructor with sensible defaults.
     */
    public FetcherConfig() {
        this(1000, 10000, "CrawlerPOC/1.0", 2);
    }
}
