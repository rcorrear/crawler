package com.example.crawler.shared;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Entity for tracking URLs in the crawl lifecycle.
 */
@Entity
@Table(name = "seen_urls")
public class SeenUrl {

    @Id
    @Column(name = "url")
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UrlStatus status;

    @Column(name = "seen_at", nullable = false)
    private Instant seenAt;

    protected SeenUrl() {} // JPA no-args constructor

    public SeenUrl(String url, UrlStatus status, Instant seenAt) {
        this.url = url;
        this.status = status;
        this.seenAt = seenAt;
    }

    /**
     * Creates a SeenUrl with the given status and current timestamp.
     */
    public static SeenUrl of(String url, UrlStatus status) {
        return new SeenUrl(url, status, Instant.now());
    }

    public String getUrl() {
        return url;
    }

    public UrlStatus getStatus() {
        return status;
    }

    public Instant getSeenAt() {
        return seenAt;
    }

    /**
     * Updates the status of this URL.
     */
    public void setStatus(UrlStatus status) {
        this.status = status;
    }
}
