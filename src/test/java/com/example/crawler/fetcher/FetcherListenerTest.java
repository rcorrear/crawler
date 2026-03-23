package com.example.crawler.fetcher;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.example.crawler.shared.CrawlError;
import com.example.crawler.shared.CrawlUrl;
import com.example.crawler.shared.PageType;
import com.example.crawler.shared.RawPage;
import com.example.crawler.shared.SeenUrlService;
import com.example.crawler.shared.Topics;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/**
 * Unit tests for FetcherListener with mocked dependencies.
 */
@ExtendWith(MockitoExtension.class)
class FetcherListenerTest {

    @Mock
    private FetcherService fetcherService;

    @Mock
    private SeenUrlService seenUrlService;

    @Mock
    private KafkaTemplate<String, RawPage> rawPageTemplate;

    @Mock
    private KafkaTemplate<String, CrawlError> errorTemplate;

    private FetcherListener listener;

    @BeforeEach
    void setUp() {
        listener = new FetcherListener(
                fetcherService,
                seenUrlService,
                rawPageTemplate,
                errorTemplate);
    }

    @Nested
    @DisplayName("when URL is not yet fetched")
    class UrlNotYetFetched {

        @Test
        @DisplayName("should fetch URL, produce RawPage, and mark as fetched")
        void shouldFetchAndProduce() throws Exception {
            // Given
            CrawlUrl crawlUrl = new CrawlUrl(
                    "https://example.com/page",
                    "test",
                    0,
                    PageType.LISTING);

            when(seenUrlService.markAsFetchedIfNotProcessed(anyString())).thenReturn(true);
            when(fetcherService.fetch(anyString()))
                    .thenReturn(new FetcherService.FetchResult(200, "<html>content</html>"));
            when(rawPageTemplate.send(anyString(), anyString(), any(RawPage.class)))
                    .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

            // When
            listener.onUrl(crawlUrl);

            // Then
            verify(fetcherService).fetch("https://example.com/page");
            verify(rawPageTemplate).send(eq(Topics.RAW_PAGES), eq("https://example.com/page"), any(RawPage.class));
            verify(seenUrlService).markAsFetchedIfNotProcessed("https://example.com/page");
            verify(errorTemplate, never()).send(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("should handle fetch error and produce CrawlError")
        void shouldHandleFetchError() throws Exception {
            // Given
            CrawlUrl crawlUrl = new CrawlUrl(
                    "https://example.com/bad-page",
                    "test",
                    0,
                    PageType.DETAIL);

            when(seenUrlService.markAsFetchedIfNotProcessed(anyString())).thenReturn(true);
            when(fetcherService.fetch(anyString()))
                    .thenThrow(new FetchException("https://example.com/bad-page", "Connection timeout", null));
            when(errorTemplate.send(anyString(), anyString(), any(CrawlError.class)))
                    .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

            // When
            listener.onUrl(crawlUrl);

            // Then
            verify(seenUrlService).markAsFailed("https://example.com/bad-page");
            verify(errorTemplate).send(eq(Topics.ERRORS), eq("https://example.com/bad-page"), any(CrawlError.class));
            verify(rawPageTemplate, never()).send(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("should handle Kafka send error")
        void shouldHandleKafkaSendError() throws Exception {
            // Given
            CrawlUrl crawlUrl = new CrawlUrl(
                    "https://example.com/page",
                    "test",
                    0,
                    PageType.LISTING);

            when(seenUrlService.markAsFetchedIfNotProcessed(anyString())).thenReturn(true);
            when(fetcherService.fetch(anyString()))
                    .thenReturn(new FetcherService.FetchResult(200, "<html>content</html>"));
            when(rawPageTemplate.send(anyString(), anyString(), any(RawPage.class)))
                    .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Kafka error")));
            when(errorTemplate.send(anyString(), anyString(), any(CrawlError.class)))
                    .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

            // When
            listener.onUrl(crawlUrl);

            // Then
            verify(seenUrlService).markAsFailed("https://example.com/page");
            verify(errorTemplate).send(eq(Topics.ERRORS), eq("https://example.com/page"), any(CrawlError.class));
        }
    }

    @Nested
    @DisplayName("when URL is already fetched")
    class UrlAlreadyFetched {

        @Test
        @DisplayName("should skip and not fetch again")
        void shouldSkipFetchedUrl() throws Exception {
            // Given
            CrawlUrl crawlUrl = new CrawlUrl(
                    "https://example.com/already-fetched",
                    "test",
                    0,
                    PageType.LISTING);

            when(seenUrlService.markAsFetchedIfNotProcessed(anyString())).thenReturn(false);

            // When
            listener.onUrl(crawlUrl);

            // Then
            verify(fetcherService, never()).fetch(anyString());
            verify(rawPageTemplate, never()).send(anyString(), anyString(), any());
            verify(errorTemplate, never()).send(anyString(), anyString(), any());
        }
    }
}
