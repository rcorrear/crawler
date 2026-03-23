package com.example.crawler.storage;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for attorney profiles.
 */
@Repository
public interface AttorneyProfileRepository extends JpaRepository<AttorneyProfileEntity, Long> {

    /**
     * Finds a profile by URL.
     */
    Optional<AttorneyProfileEntity> findByUrl(String url);

    /**
     * Checks if a profile exists by URL.
     */
    boolean existsByUrl(String url);
}
