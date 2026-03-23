package com.example.crawler.storage;

import com.example.crawler.shared.AttorneyProfile;
import com.example.crawler.shared.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka listener that stores attorney profiles to Postgres.
 */
@Component
class StorageListener {

    private static final Logger log = LoggerFactory.getLogger(StorageListener.class);

    private final StorageService storageService;

    StorageListener(StorageService storageService) {
        this.storageService = storageService;
    }

    @KafkaListener(
            topics = Topics.PARSED_ITEMS,
            groupId = "crawler-storage",
            containerFactory = "attorneyProfileListenerContainerFactory")
    void onProfile(AttorneyProfile profile) {
        log.info("Storing profile: {}", profile.url());
        storageService.upsert(profile);
        log.info("Stored profile: {} ({})", profile.name(), profile.url());
    }
}
