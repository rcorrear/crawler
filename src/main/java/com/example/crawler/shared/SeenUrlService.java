package com.example.crawler.shared;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for tracking URLs in the crawl lifecycle.
 */
@Service
public class SeenUrlService {

    private final SeenUrlRepository repository;

    public SeenUrlService(SeenUrlRepository repository) {
        this.repository = repository;
    }

    // === SCHEDULER METHODS ===

    /**
     * Checks if a URL has already been scheduled.
     */
    public boolean hasBeenScheduled(String url) {
        return repository.existsByUrlAndStatus(url, UrlStatus.SCHEDULED);
    }

    /**
     * Marks a URL as SCHEDULED if it hasn't been seen before, or re-schedules a FAILED URL.
     * Returns true if the URL was newly scheduled or re-scheduled from FAILED, false if already scheduled/fetched/processed.
     */
    @Transactional
    public boolean markAsScheduledIfNew(String url) {
        return repository.findByUrl(url).map(seenUrl -> {
            if (seenUrl.getStatus() == UrlStatus.FAILED) {
                // Re-schedule previously failed URL
                seenUrl.setStatus(UrlStatus.SCHEDULED);
                repository.save(seenUrl);
                return true;
            }
            // Already exists with non-FAILED status
            return false;
        }).orElseGet(() -> {
            // New URL
            repository.save(SeenUrl.of(url, UrlStatus.SCHEDULED));
            return true;
        });
    }

    // === FETCHER METHODS ===

    /**
     * Checks if a URL has already been fetched.
     */
    public boolean hasBeenFetched(String url) {
        return repository.existsByUrlAndStatus(url, UrlStatus.FETCHED);
    }

    /**
     * Checks if a URL has been processed by the parser.
     */
    public boolean hasBeenProcessed(String url) {
        return repository.existsByUrlAndStatus(url, UrlStatus.PROCESSED);
    }

    /**
     * Atomically marks a URL as FETCHED if not already processed.
     * Creates the entry if it doesn't exist.
     * Returns true if this call successfully marked it (caller should proceed),
     * false if already FETCHED or PROCESSED (caller should skip).
     */
    @Transactional
    public boolean markAsFetchedIfNotProcessed(String url) {
        return repository.findByUrl(url).map(seenUrl -> {
            // Already processed - skip
            if (seenUrl.getStatus() == UrlStatus.FETCHED || seenUrl.getStatus() == UrlStatus.PROCESSED) {
                return false;
            }
            // SCHEDULED or FAILED - mark as FETCHED
            seenUrl.setStatus(UrlStatus.FETCHED);
            SeenUrl saved = repository.save(seenUrl);
            return true;
        }).orElseGet(() -> {
            // URL doesn't exist - create as FETCHED
            SeenUrl saved = repository.save(SeenUrl.of(url, UrlStatus.FETCHED));
            return true;
        });
    }

    /**
     * Updates a URL's status to FETCHED.
     * Creates the entry if it doesn't exist (handles URLs that bypassed scheduler).
     * Returns true if the status was updated or created, false if already fetched/processed.
     */
    @Transactional
    public boolean markAsFetched(String url) {
        return repository.findByUrl(url).map(seenUrl -> {
            if (seenUrl.getStatus() == UrlStatus.SCHEDULED || seenUrl.getStatus() == UrlStatus.FAILED) {
                seenUrl.setStatus(UrlStatus.FETCHED);
                repository.save(seenUrl);
                return true;
            }
            // Already FETCHED or PROCESSED
            return false;
        }).orElseGet(() -> {
            // URL doesn't exist (bypassed scheduler) - create as FETCHED
            repository.save(SeenUrl.of(url, UrlStatus.FETCHED));
            return true;
        });
    }

    /**
     * Marks a URL as FAILED.
     * Creates the entry if it doesn't exist (handles URLs that bypassed scheduler).
     */
    @Transactional
    public void markAsFailed(String url) {
        repository.findByUrl(url).ifPresentOrElse(
                seenUrl -> {
                    seenUrl.setStatus(UrlStatus.FAILED);
                    repository.save(seenUrl);
                },
                () -> {
                    // URL doesn't exist (bypassed scheduler) - create as FAILED
                    repository.save(SeenUrl.of(url, UrlStatus.FAILED));
                });
    }

    // === PARSER METHODS ===

    /**
     * Updates a URL's status to PROCESSED.
     * Returns true if the status was updated, false if not found.
     */
    @Transactional
    public boolean markAsProcessed(String url) {
        return repository.findByUrl(url).map(seenUrl -> {
            seenUrl.setStatus(UrlStatus.PROCESSED);
            repository.save(seenUrl);
            return true;
        }).orElse(false);
    }

    // === LEGACY COMPATIBILITY ===

    /**
     * Checks if a URL has already been seen (any status).
     * @deprecated Use hasBeenScheduled() or hasBeenFetched() instead.
     */
    @Deprecated
    public boolean hasBeenSeen(String url) {
        return repository.existsByUrl(url);
    }

    /**
     * Marks a URL as seen if it hasn't been seen before.
     * @deprecated Use markAsScheduledIfNew() instead.
     */
    @Deprecated
    @Transactional
    public boolean markAsSeenIfNew(String url) {
        return markAsScheduledIfNew(url);
    }
}
