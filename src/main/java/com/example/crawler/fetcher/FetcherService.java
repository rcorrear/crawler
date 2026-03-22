package com.example.crawler.fetcher;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Service for fetching HTML content from URLs.
 */
@Service
public class FetcherService {

    private static final Logger log = LoggerFactory.getLogger(FetcherService.class);

    private final RestClient restClient;
    private final FetcherConfig config;

    public FetcherService(FetcherConfig config) {
        this.config = config;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(config.timeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(config.timeoutMs()));

        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("User-Agent", config.userAgent())
                .defaultHeader("Accept", "text/html,application/xhtml+xml")
                .build();
    }

    /**
     * Fetches HTML content from a URL with retry logic.
     *
     * @param url the URL to fetch
     * @return FetchResult containing the HTML and HTTP status code
     * @throws FetchException if the fetch fails after all retries
     */
    @Retryable(
            retryFor = RestClientException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000))
    public FetchResult fetch(String url) throws FetchException {
        // Apply politeness delay before each request
        if (config.delayMs() > 0) {
            try {
                Thread.sleep(config.delayMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FetchException(url, "Interrupted during fetch", e);
            }
        }

        log.debug("Fetching: {}", url);

        var response = restClient.get()
                .uri(url)
                .retrieve()
                .toEntity(String.class);

        HttpStatusCode status = response.getStatusCode();
        String html = response.getBody();

        if (status.is2xxSuccessful() && html != null) {
            log.info(
                    "Fetched {} bytes from {} (status={})",
                    html.length(),
                    url,
                    status.value());
            return new FetchResult(status.value(), html);
        }

        // Non-2xx status - treat as error
        throw new FetchException(url, "HTTP " + status.value(), null);
    }

    /**
     * Recovery method called when all retry attempts fail.
     */
    @Recover
    public FetchResult recover(RestClientException e, String url) throws FetchException {
        log.error("All retry attempts failed for {}: {}", url, e.getMessage());
        throw new FetchException(url, "Connection error after retries: " + e.getMessage(), e);
    }

    /**
     * Result of a successful fetch operation.
     */
    public record FetchResult(int status, String html) {}
}
