package com.example.crawler;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Verifies Modulith module structure.
 * Ensures all modules only depend on 'shared' and not on each other.
 */
class ModulithVerificationTest {

    @Test
    void verifyModulithStructure() {
        ApplicationModules modules = ApplicationModules.of(CrawlerApplication.class);

        // Verify no illegal cross-module dependencies
        modules.verify();

        // Assert each module exists
        assertThat(modules).isNotEmpty();
    }
}
