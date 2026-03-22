package com.example.crawler.shared;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Attorney profile extracted from the website.
 *
 * @param url the canonical URL (detail page URL)
 * @param name the attorney's display name
 * @param surname the attorney's surname
 * @param title the attorney's title (e.g., "TRIAL ATTORNEY")
 * @param location the office location (e.g., "Orlando, FL")
 * @param phone the phone number
 * @param email the email address
 * @param photoUrl the URL to the attorney's photo
 * @param bio the attorney's biography
 * @param practiceAreas list of practice areas
 * @param education list of education entries
 * @param barAdmissions list of bar admissions
 * @param memberships list of professional memberships
 * @param socialLinks map of social media platform to URL
 * @param officeLocations list of office locations
 * @param source the source identifier
 * @param extractedAt the timestamp when the profile was extracted
 */
public record AttorneyProfile(
        String url,
        String name,
        String surname,
        String title,
        String location,
        String phone,
        String email,
        String photoUrl,
        String bio,
        List<String> practiceAreas,
        List<String> education,
        List<String> barAdmissions,
        List<String> memberships,
        Map<String, String> socialLinks,
        List<OfficeLocation> officeLocations,
        String source,
        Instant extractedAt
) {
    /**
     * Creates an AttorneyProfile with default empty collections.
     */
    public AttorneyProfile {
        practiceAreas = practiceAreas != null ? List.copyOf(practiceAreas) : List.of();
        education = education != null ? List.copyOf(education) : List.of();
        barAdmissions = barAdmissions != null ? List.copyOf(barAdmissions) : List.of();
        memberships = memberships != null ? List.copyOf(memberships) : List.of();
        socialLinks = socialLinks != null ? Map.copyOf(socialLinks) : Map.of();
        officeLocations = officeLocations != null ? List.copyOf(officeLocations) : List.of();
    }
}
