package com.example.crawler.shared;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for tracking URLs that have been scheduled/fetched.
 */
@Service
public class SeenUrlService {

    private final SeenUrlRepository repository;

    public SeenUrlService(SeenUrlRepository repository) {
        this.repository = repository;
    }

    /**
     * Marks a URL as seen if it hasn't been seen before.
     * Returns true if the URL was newly marked, false if it was already seen.
     */
    @Transactional
    public boolean markAsSeenIfNew(String url) {
        if (repository.existsByUrl(url)) {
            return false;
        }
        repository.save(SeenUrl.of(url));
        return true;
    }

    /**
     * Checks if a URL has already been seen.
     */
    public boolean hasBeenSeen(String url) {
        return repository.existsByUrl(url);
    }
}
