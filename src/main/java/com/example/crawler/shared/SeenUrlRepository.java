package com.example.crawler.shared;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for tracking URLs that have been scheduled/fetched.
 */
@Repository
public interface SeenUrlRepository extends JpaRepository<SeenUrl, String> {

    /**
     * Checks if a URL has already been seen.
     */
    boolean existsByUrl(String url);
}
