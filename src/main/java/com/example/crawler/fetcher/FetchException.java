package com.example.crawler.fetcher;

/**
 * Exception thrown when a fetch operation fails.
 */
public class FetchException extends Exception {

    private final String url;

    public FetchException(String url, String message, Throwable cause) {
        super(message, cause);
        this.url = url;
    }

    public String getUrl() {
        return url;
    }
}
