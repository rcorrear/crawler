package com.example.crawler.shared;

import java.time.Instant;

/**
 * Raw HTML page fetched from a URL.
 *
 * @param url the URL that was fetched
 * @param status the HTTP status code
 * @param html the HTML content
 * @param source the source identifier
 * @param depth the crawl depth
 * @param pageType the type of page
 * @param fetchedAt the timestamp when the page was fetched
 */
public record RawPage(
        String url,
        int status,
        String html,
        String source,
        int depth,
        PageType pageType,
        Instant fetchedAt
) {}
