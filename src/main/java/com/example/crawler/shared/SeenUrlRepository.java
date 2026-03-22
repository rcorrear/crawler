package com.example.crawler.shared;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for tracking URLs in the crawl lifecycle.
 */
@Repository
public interface SeenUrlRepository extends JpaRepository<SeenUrl, String> {

    /**
     * Finds a SeenUrl by its URL.
     */
    Optional<SeenUrl> findByUrl(String url);

    /**
     * Checks if a URL exists with a specific status.
     */
    boolean existsByUrlAndStatus(String url, UrlStatus status);

    /**
     * Checks if a URL has already been seen (any status).
     */
    default boolean existsByUrl(String url) {
        return findByUrl(url).isPresent();
    }
}
