package com.example.crawler.shared;


/**
 * URL to be crawled.
 *
 * @param url the URL to crawl
 * @param source the source identifier (e.g., "forthepeople")
 * @param depth the crawl depth (0 = listing, 1 = detail)
 * @param pageType the type of page
 */
public record CrawlUrl(
        String url,
        String source,
        int depth,
        PageType pageType
) {}
