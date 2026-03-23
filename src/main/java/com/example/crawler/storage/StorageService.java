package com.example.crawler.storage;

import com.example.crawler.shared.AttorneyProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for storing attorney profiles with upsert logic.
 */
@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private final AttorneyProfileRepository repository;

    public StorageService(AttorneyProfileRepository repository) {
        this.repository = repository;
    }

    /**
     * Stores or updates an attorney profile.
     * If URL exists, merges non-null fields (detail enriches listing).
     */
    @Transactional
    public void upsert(AttorneyProfile profile) {
        repository.findByUrl(profile.url()).ifPresentOrElse(
                existing -> {
                    // Merge: detail enriches listing
                    AttorneyProfileEntity update = AttorneyProfileEntity.fromRecord(profile);
                    existing.mergeFrom(update);
                    repository.save(existing);
                    log.debug("Updated profile: {}", profile.url());
                },
                () -> {
                    // New profile
                    AttorneyProfileEntity entity = AttorneyProfileEntity.fromRecord(profile);
                    repository.save(entity);
                    log.debug("Created profile: {}", profile.url());
                });
    }
}
