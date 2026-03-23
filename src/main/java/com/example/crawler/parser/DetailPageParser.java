package com.example.crawler.parser;

import com.example.crawler.shared.AttorneyProfile;
import com.example.crawler.shared.OfficeLocation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Parser for attorney detail pages (/attorneys/{slug}/).
 * Extracts complete profile data using Jsoup selectors.
 */
@Component
public class DetailPageParser {

    private static final Logger log = LoggerFactory.getLogger(DetailPageParser.class);

    /**
     * Parses an attorney detail page HTML and extracts the complete profile.
     *
     * @param html the HTML content of the detail page
     * @param url the URL of the page (used as canonical URL)
     * @param source the source identifier
     * @return AttorneyProfile with all extractable fields
     */
    public AttorneyProfile parse(String html, String url, String source) {
        Document doc = Jsoup.parse(html);
        Instant now = Instant.now();

        // Sidebar info
        String name = extractText(doc, "div.attorney-sidebar-info h1");
        String title = extractTitle(doc);
        String office = extractText(doc, "div.attorney-office a");
        String phone = extractPhone(doc);
        String email = extractEmail(doc);

        // Social links
        Map<String, String> socialLinks = new HashMap<>();
        extractSocialLink(doc, "div.attorney-linkedin a", "linkedin", socialLinks);
        extractSocialLink(doc, "div.attorney-facebook a", "facebook", socialLinks);
        extractSocialLink(doc, "div.attorney-twitter a", "twitter", socialLinks);
        extractSocialLink(doc, "div.attorney-instagram a", "instagram", socialLinks);

        // Photo
        String photoUrl = extractPhotoUrl(doc);

        // Bio
        String bio = extractText(doc, "div.block-attorney-single-body__octane");

        // Education
        List<String> education = extractEachText(doc, "div.educations-list li");

        // Bar admissions
        List<String> barAdmissions = extractEachText(doc, "div.bar-admission-list li");

        // Memberships
        List<String> memberships = extractEachText(doc, "div.memberships-list li");

        // Practice areas (if available on detail page)
        List<String> practiceAreas = extractEachText(doc, "div.practice-areas-list li");
        if (practiceAreas.isEmpty()) {
            // Alternative selector
            practiceAreas = extractEachText(doc, "div.attorney-practice-areas a");
        }

        // Create office location from office string
        List<OfficeLocation> officeLocations = parseOfficeString(office);

        log.debug("Parsed detail page for {}: {} ({})", name, title, office);

        return new AttorneyProfile(
                url,
                name,
                extractSurname(name),
                title,
                office,
                phone,
                email,
                photoUrl,
                bio,
                practiceAreas,
                education,
                barAdmissions,
                memberships,
                socialLinks,
                officeLocations,
                source,
                now);
    }

    private String extractText(Document doc, String selector) {
        Element el = doc.selectFirst(selector);
        return el != null ? el.text().trim() : null;
    }

    private List<String> extractEachText(Document doc, String selector) {
        List<String> results = new ArrayList<>();
        Elements elements = doc.select(selector);
        for (Element el : elements) {
            String text = el.text().trim();
            if (!text.isEmpty()) {
                results.add(text);
            }
        }
        return results;
    }

    private String extractTitle(Document doc) {
        // Try body-r-bold first (for most attorneys)
        Element el = doc.selectFirst("div.attorney-sidebar-info p.body-r-bold");
        if (el != null) {
            return el.text().trim();
        }
        // Fall back to body-md
        el = doc.selectFirst("div.attorney-sidebar-info p.body-md");
        if (el != null) {
            return el.text().trim();
        }
        // Try the wrapper for special cases (e.g., John Morgan)
        el = doc.selectFirst("div.attorney-professional-title-wrapper p");
        if (el != null) {
            return el.text().trim();
        }
        return null;
    }

    private String extractPhone(Document doc) {
        Element el = doc.selectFirst("div.attorney-phone a[href^=tel]");
        if (el != null) {
            String href = el.attr("href");
            if (href.startsWith("tel:")) {
                return href.substring(4);
            }
            return href;
        }
        return null;
    }

    private String extractEmail(Document doc) {
        Element el = doc.selectFirst("div.attorney-email a[href^=mailto]");
        if (el != null) {
            String href = el.attr("href");
            if (href.startsWith("mailto:")) {
                return href.substring(7);
            }
            return href;
        }
        return null;
    }

    private void extractSocialLink(Document doc, String selector, String platform, Map<String, String> socialLinks) {
        Element el = doc.selectFirst(selector);
        if (el != null) {
            String href = el.attr("href");
            if (href != null && !href.isEmpty()) {
                socialLinks.put(platform, href);
            }
        }
    }

    private String extractPhotoUrl(Document doc) {
        Element el = doc.selectFirst("div.attorney-sidebar-photo source:first-of-type");
        if (el != null) {
            String srcset = el.attr("srcset");
            if (srcset != null && !srcset.isEmpty()) {
                // srcset may contain multiple URLs with sizes, take the first one
                String[] parts = srcset.split("\\s+");
                if (parts.length > 0) {
                    return parts[0];
                }
            }
        }
        return null;
    }

    private String extractSurname(String fullName) {
        if (fullName == null || fullName.isEmpty()) {
            return null;
        }
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length > 0) {
            return parts[parts.length - 1];
        }
        return null;
    }

    private List<OfficeLocation> parseOfficeString(String office) {
        if (office == null || office.isEmpty()) {
            return List.of();
        }
        // Format is typically "City, ST" (e.g., "Orlando, FL")
        String[] parts = office.split(",");
        if (parts.length == 2) {
            String city = parts[0].trim();
            String stateAbbr = parts[1].trim();
            return List.of(new OfficeLocation(null, city, stateAbbr));
        }
        return List.of();
    }
}
