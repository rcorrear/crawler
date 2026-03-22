package com.example.crawler.shared;

/**
 * Status of a URL in the crawl lifecycle.
 */
public enum UrlStatus {
    SCHEDULED,
    FETCHED,
    PROCESSED,
    FAILED
}
