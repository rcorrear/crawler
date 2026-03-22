package com.example.crawler.shared;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Entity for tracking URLs that have been scheduled/fetched.
 */
@Entity
@Table(name = "seen_urls")
public class SeenUrl {

    @Id
    @Column(name = "url")
    private String url;

    @Column(name = "seen_at", nullable = false)
    private Instant seenAt;

    protected SeenUrl() {} // JPA no-args constructor

    public SeenUrl(String url, Instant seenAt) {
        this.url = url;
        this.seenAt = seenAt;
    }

    /**
     * Creates a SeenUrl with the current timestamp.
     */
    public static SeenUrl of(String url) {
        return new SeenUrl(url, Instant.now());
    }

    public String getUrl() {
        return url;
    }

    public Instant getSeenAt() {
        return seenAt;
    }
}
