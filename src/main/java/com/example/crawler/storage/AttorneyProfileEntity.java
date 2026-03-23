package com.example.crawler.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity for attorney profiles.
 * Maps to the attorney_profiles table.
 */
@Entity
@Table(name = "attorney_profiles")
public class AttorneyProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String url;

    private String name;
    private String surname;
    private String title;
    private String location;
    private String phone;
    private String email;

    @Column(name = "photo_url")
    private String photoUrl;

    private String bio;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String practiceAreas = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String education = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String barAdmissions = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String memberships = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String socialLinks = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String officeLocations = "[]";

    private String source;

    @Column(name = "extracted_at")
    private Instant extractedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AttorneyProfileEntity() {} // JPA no-args constructor

    /**
     * Creates a new entity from an AttorneyProfile record.
     */
    public static AttorneyProfileEntity fromRecord(com.example.crawler.shared.AttorneyProfile profile) {
        AttorneyProfileEntity entity = new AttorneyProfileEntity();
        entity.url = profile.url();
        entity.name = profile.name();
        entity.surname = profile.surname();
        entity.title = profile.title();
        entity.location = profile.location();
        entity.phone = profile.phone();
        entity.email = profile.email();
        entity.photoUrl = profile.photoUrl();
        entity.bio = profile.bio();
        entity.practiceAreas = toJsonArray(profile.practiceAreas());
        entity.education = toJsonArray(profile.education());
        entity.barAdmissions = toJsonArray(profile.barAdmissions());
        entity.memberships = toJsonArray(profile.memberships());
        entity.socialLinks = toJsonObject(profile.socialLinks());
        entity.officeLocations = toJsonArray(profile.officeLocations().stream()
                .map(loc -> String.format("{\"state\":\"%s\",\"city\":\"%s\",\"abbreviation\":\"%s\"}",
                        escapeJson(loc.state()),
                        escapeJson(loc.city()),
                        escapeJson(loc.abbreviation())))
                .toList());
        entity.source = profile.source();
        entity.extractedAt = profile.extractedAt();
        entity.createdAt = Instant.now();
        entity.updatedAt = Instant.now();
        return entity;
    }

    /**
     * Merges non-null fields from another entity (detail enriches listing).
     */
    public void mergeFrom(AttorneyProfileEntity other) {
        if (other.name != null) this.name = other.name;
        if (other.surname != null) this.surname = other.surname;
        if (other.title != null) this.title = other.title;
        if (other.location != null) this.location = other.location;
        if (other.phone != null) this.phone = other.phone;
        if (other.email != null) this.email = other.email;
        if (other.photoUrl != null) this.photoUrl = other.photoUrl;
        if (other.bio != null) this.bio = other.bio;
        if (other.practiceAreas != null && !other.practiceAreas.equals("[]")) {
            this.practiceAreas = other.practiceAreas;
        }
        if (other.education != null && !other.education.equals("[]")) {
            this.education = other.education;
        }
        if (other.barAdmissions != null && !other.barAdmissions.equals("[]")) {
            this.barAdmissions = other.barAdmissions;
        }
        if (other.memberships != null && !other.memberships.equals("[]")) {
            this.memberships = other.memberships;
        }
        if (other.socialLinks != null && !other.socialLinks.equals("{}")) {
            this.socialLinks = other.socialLinks;
        }
        if (other.officeLocations != null && !other.officeLocations.equals("[]")) {
            this.officeLocations = other.officeLocations;
        }
        if (other.source != null) this.source = other.source;
        if (other.extractedAt != null) this.extractedAt = other.extractedAt;
        this.updatedAt = Instant.now();
    }

    private static String toJsonArray(java.util.List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escapeJson(list.get(i))).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String toJsonArray(java.util.List<String> list, boolean isObjectArray) {
        if (!isObjectArray) return toJsonArray(list);
        if (list == null || list.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(list.get(i));
        }
        sb.append("]");
        return sb.toString();
    }

    private static String toJsonObject(java.util.Map<String, String> map) {
        if (map == null || map.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (var entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(escapeJson(entry.getKey())).append("\":");
            sb.append("\"").append(escapeJson(entry.getValue())).append("\"");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // Getters
    public Long getId() { return id; }
    public String getUrl() { return url; }
    public String getName() { return name; }
    public String getSurname() { return surname; }
    public String getTitle() { return title; }
    public String getLocation() { return location; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getPhotoUrl() { return photoUrl; }
    public String getBio() { return bio; }
    public String getPracticeAreas() { return practiceAreas; }
    public String getEducation() { return education; }
    public String getBarAdmissions() { return barAdmissions; }
    public String getMemberships() { return memberships; }
    public String getSocialLinks() { return socialLinks; }
    public String getOfficeLocations() { return officeLocations; }
    public String getSource() { return source; }
    public Instant getExtractedAt() { return extractedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
