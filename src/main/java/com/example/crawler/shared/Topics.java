package com.example.crawler.shared;

/**
 * Topic name constants for the crawler pipeline.
 */
public final class Topics {

    private Topics() {}

    public static final String URLS_TO_CRAWL = "urls_to_crawl";
    public static final String RAW_PAGES = "raw_pages";
    public static final String PARSED_ITEMS = "parsed_items";
    public static final String ERRORS = "errors";
}
