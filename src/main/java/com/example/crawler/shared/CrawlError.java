package com.example.crawler.shared;

import java.time.Instant;

/**
 * Error that occurred during crawling.
 *
 * @param url the URL that caused the error
 * @param stage the stage where the error occurred (e.g., "fetcher", "parser")
 * @param error the error message
 * @param occurredAt the timestamp when the error occurred
 */
public record CrawlError(
        String url,
        String stage,
        String error,
        Instant occurredAt
) {}
