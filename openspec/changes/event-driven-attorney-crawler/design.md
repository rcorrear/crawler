# Design — Event-Driven Attorney Crawler POC

## 1. Site Structure Discovery

### Listing Page (`/attorneys/?page=N`)

The listing page does **not** use HTML cards for attorneys. Instead, the full attorney directory (all 1,100 attorneys) is embedded as a JSON array inside a `<script>` tag within `drupalSettings`:

```
<script type="application/json" data-drupal-selector="drupal-settings-json">
{
  ...
  "attorneys_master_list": {
    "data": [ ... array of 1100 attorney objects ... ]
  }
  ...
}
</script>
```

The client-side JS handles pagination, filtering, and rendering from this JSON blob. This means:

**Key insight:** We only need to fetch the listing page **once** (any `?page=N` returns the same JSON blob). The pagination is client-side only. One fetch gives us all 1,100 attorneys.

#### Attorney JSON object (from listing):

```json
{
  "id": 538,
  "name": "Daniel Watts",
  "surName": "Watts",
  "featured": "",
  "sticky": "",
  "officeLocations": [
    {
      "State": "New York",
      "City": "New York",
      "Abbreviation": "NY"
    }
  ],
  "offices": ["New York"],
  "photo": "https://www.forthepeople.com/.../Dan_Watts_1000x1000.png?itok=...",
  "photoAlt": "Daniel Watts",
  "alt": "Attorney Daniel Watts",
  "practiceAreas": [
    "Traumatic Brain Injury",
    "Car Accident",
    "Personal Injury",
    ...
  ],
  "url": "/attorneys/daniel-watts/"
}
```

**Fields available from listing JSON (no detail page needed):**
- `id` — internal Drupal node ID
- `name` — display name
- `surName` — last name
- `officeLocations` — array of `{State, City, Abbreviation}`
- `offices` — array of city names
- `photo` — full URL to headshot
- `practiceAreas` — array of practice area strings
- `url` — relative path to detail page

### Detail Page (`/attorneys/{slug}/`)

Server-rendered HTML. Selectors are consistent across attorneys (verified on multiple profiles).

#### Sidebar (name, title, contact)

```html
<div class="attorney-sidebar-info">
  <!-- Name: h1 with <br /> between parts -->
  <h1 class="h3">Blake <br /> J. <br /> Lange</h1>
  
  <!-- Title: p tag directly after h1 (NOT in a wrapper for most attorneys) -->
  <!-- OR inside attorney-professional-title-wrapper for some (e.g., John Morgan) -->
  <p class="body-r-bold">FOUNDER & ATTORNEY</p>
  <!-- OR -->
  <p class="body-md">TRIAL ATTORNEY</p>
  
  <!-- Social links (only present for some attorneys) -->
  <div class="social-media-wrapper">
    <div class="attorney-linkedin">
      <a href="https://www.linkedin.com/in/..." target="_blank">...</a>
    </div>
    <div class="attorney-facebook">
      <a href="https://www.facebook.com/..." target="_blank">...</a>
    </div>
    <div class="attorney-twitter">
      <a href="https://twitter.com/..." target="_blank">...</a>
    </div>
    <div class="attorney-instagram">
      <a href="https://www.instagram.com/..." target="_blank">...</a>
    </div>
  </div>
  
  <!-- Contact info -->
  <div class="attorney-contact-info">
    <div class="attorney-contact-item attorney-office">
      <a href="/office-locations/florida/orlando/">Orlando, FL</a>
    </div>
    <div class="attorney-contact-item attorney-phone">
      <a href="tel:+14074201414">(407) 420-1414</a>
    </div>
    <div class="attorney-contact-item attorney-email">
      <a href="mailto:johnmorgan@forthepeople.com">johnmorgan@forthepeople.com</a>
    </div>
  </div>
</div>
```

**Jsoup selectors for sidebar:**

| Field | Selector | Extraction |
|-------|----------|------------|
| Name | `div.attorney-sidebar-info h1` | `.text()` (Jsoup strips `<br />` as spaces) |
| Title | `div.attorney-sidebar-info p.body-r-bold` OR `p.body-md` (first `<p>` after `<h1>`) | `.text()` |
| Office | `div.attorney-office a` | `.text()` → "Orlando, FL" |
| Phone | `div.attorney-phone a[href^=tel]` | `.attr("href")` strip `tel:` prefix |
| Email | `div.attorney-email a[href^=mailto]` | `.attr("href")` strip `mailto:` prefix |
| LinkedIn | `div.attorney-linkedin a` | `.attr("href")` |
| Facebook | `div.attorney-facebook a` | `.attr("href")` |
| X/Twitter | `div.attorney-twitter a` | `.attr("href")` |
| Instagram | `div.attorney-instagram a` | `.attr("href")` |

#### Photo

```html
<div class="attorney-sidebar-photo">
  <picture>
    <source srcset="https://...495x436/image.webp 1x" media="(max-width: 575px)" />
    <source srcset="https://...640x564/image.webp 2x" media="(min-width: 576px)" />
    ...
  </picture>
</div>
```

**Selector:** `div.attorney-sidebar-photo source:first-of-type` → `.attr("srcset")` (take first URL before space).

*Note: We already get a photo URL from the listing JSON. The detail page provides higher-resolution variants. For POC, the listing-level photo is sufficient.*

#### Bio

```html
<div class="block block-ftp-blocks block-attorney-single-body__octane">
  <h2 class="h3">Blake Lange is a longstanding member of the community...</h2>
  <p>Blake Lange is the managing partner of Morgan & Morgan's...</p>
  ...
</div>
```

**Selector:** `div.block-attorney-single-body__octane` → `.text()` for full bio, or iterate children for structured content.

#### Education, Bar Admissions, Memberships

```html
<div class="secondary-info-list educations-list col-12 col-xl-4 color-bg-n-100">
  <h3 class="h5">Education</h3>
  <ul>
    <li>University of Florida, Bachelor of Arts, 1978</li>
    <li>University of Florida's Levin College of Law, Juris Doctor, 1982</li>
  </ul>
</div>

<div class="secondary-info-list bar-admission-list col-12 col-xl-4 color-bg-n-100">
  <h3 class="h5">Bar Admissions</h3>
  <ul>
    <li>State Bar of Florida (1984 - Present)</li>
  </ul>
</div>

<div class="secondary-info-list memberships-list col-12 col-xl-4 color-bg-n-100">
  <h3 class="h5">Memberships</h3>
  <ul>
    <li>Florida Justice Association — Board of Directors</li>
    ...
  </ul>
</div>
```

**Selectors:**

| Field | Selector | Extraction |
|-------|----------|------------|
| Education | `div.educations-list ul li` | `.eachText()` → `List<String>` |
| Bar Admissions | `div.bar-admission-list ul li` | `.eachText()` → `List<String>` |
| Memberships | `div.memberships-list ul li` | `.eachText()` → `List<String>` |

**Note:** Not all attorneys have all three sections. Some have only bar admissions or only memberships. The parser must handle missing sections gracefully (return empty list).

---

## 2. Revised Crawl Strategy

The discovery that listing data is a JSON blob changes the pipeline:

```
Scheduler (ApplicationRunner)
    │
    │  Produces 1 CrawlUrl (depth=0, pageType=LISTING)
    │  URL: https://www.forthepeople.com/attorneys/
    ▼
urls_to_crawl
    │
    ▼
Fetcher
    │  Fetches the single listing page
    ▼
raw_pages
    │
    ▼
ListingPageParser
    │  Extracts JSON blob → 1,100 attorney objects
    │  For each attorney:
    │    - Produces AttorneyProfile (listing-level: name, offices, practiceAreas, photo)
    │    - Produces CrawlUrl (depth=1, pageType=DETAIL, url=/attorneys/{slug}/)
    │
    ├──▶ parsed_items (1,100 listing-level profiles)
    └──▶ urls_to_crawl (1,100 detail page URLs, but capped per scope)
              │
              ▼
         Fetcher (fetches detail pages with configured delay)
              │
              ▼
         raw_pages
              │
              ▼
         DetailPageParser
              │  Enriches profile with bio, education, bar, memberships, socials
              ▼
         parsed_items (full profiles)
              │
              ▼
         Storage (upserts into Postgres — listing data + detail data merged by URL)
```

### Scope Control

Since we have all 1,100 attorney URLs from the JSON blob, we control scope via:

- **`crawler.max-detail-pages`** in `application.yml` (default: 160 for POC)
- The ListingPageParser produces only the first N detail-page CrawlUrls
- All 1,100 listing-level profiles are still stored (they're free — already in the JSON)

### Scheduler Simplification

The Scheduler now seeds exactly **1 URL** (the listing page). No need to produce 10 paginated URLs since pagination is client-side.

---

## 3. Message Contracts (Java Records)

### Shared Kernel (`com.example.crawler.shared`)

```java
public enum PageType { LISTING, DETAIL }

public record CrawlUrl(
    String url,
    String source,       // "forthepeople"
    int depth,           // 0 = listing, 1 = detail
    PageType pageType
) {}

public record RawPage(
    String url,
    int status,
    String html,
    String source,
    int depth,
    PageType pageType,
    Instant fetchedAt
) {}

public record AttorneyProfile(
    String url,           // canonical URL (detail page or listing-derived)
    String name,
    String surname,
    String title,         // "TRIAL ATTORNEY", "FOUNDER & ATTORNEY"
    String location,      // "Orlando, FL"
    String phone,
    String email,
    String photoUrl,
    String bio,
    List<String> practiceAreas,
    List<String> education,
    List<String> barAdmissions,
    List<String> memberships,
    Map<String, String> socialLinks,  // "linkedin" → URL, "facebook" → URL, etc.
    List<OfficeLocation> officeLocations,
    String source,
    Instant extractedAt
) {}

public record OfficeLocation(
    String state,
    String city,
    String abbreviation
) {}

public record CrawlError(
    String url,
    String stage,         // "fetcher", "parser"
    String error,
    Instant occurredAt
) {}
```

### Topic Constants

```java
public final class Topics {
    public static final String URLS_TO_CRAWL = "urls_to_crawl";
    public static final String RAW_PAGES = "raw_pages";
    public static final String PARSED_ITEMS = "parsed_items";
    public static final String ERRORS = "errors";
}
```

---

## 4. Kafka Configuration

### Producer/Consumer Setup

```yaml
# application.yml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "com.example.crawler.shared"
      auto-offset-reset: earliest
    listener:
      concurrency: 1  # single-threaded for POC (politeness)

  threads:
    virtual:
      enabled: true
```

### Consumer Groups

Each module gets its own consumer group so they consume independently:

| Module  | Consumer Group       | Listens To      |
|---------|---------------------|-----------------|
| Fetcher | `crawler-fetcher`   | `urls_to_crawl` |
| Parser  | `crawler-parser`    | `raw_pages`     |
| Storage | `crawler-storage`   | `parsed_items`  |

### Topic Configuration

Single partition per topic is fine for POC. If we wanted to parallelize the fetcher later, we'd add partitions to `urls_to_crawl` and increase listener concurrency.

---

## 5. Database Schema

### `seen_urls`

```sql
CREATE TABLE seen_urls (
    url       TEXT PRIMARY KEY,
    seen_at   TIMESTAMP NOT NULL DEFAULT NOW()
);
```

Fetcher checks: `SELECT EXISTS(SELECT 1 FROM seen_urls WHERE url = ?)` before fetching.
After fetch: `INSERT INTO seen_urls (url) VALUES (?) ON CONFLICT DO NOTHING`.

### `attorney_profiles`

```sql
CREATE TABLE attorney_profiles (
    id                BIGSERIAL PRIMARY KEY,
    url               TEXT NOT NULL UNIQUE,
    name              TEXT,
    surname           TEXT,
    title             TEXT,
    location          TEXT,
    phone             TEXT,
    email             TEXT,
    photo_url         TEXT,
    bio               TEXT,
    practice_areas    JSONB DEFAULT '[]',
    education         JSONB DEFAULT '[]',
    bar_admissions    JSONB DEFAULT '[]',
    memberships       JSONB DEFAULT '[]',
    social_links      JSONB DEFAULT '{}',
    office_locations  JSONB DEFAULT '[]',
    source            TEXT,
    extracted_at      TIMESTAMP,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP NOT NULL DEFAULT NOW()
);
```

List/map fields stored as JSONB. The Storage module upserts: if the URL already exists (from a listing-level parse), the detail-level parse updates it with enriched data (bio, education, etc.).

**Upsert strategy:**
```sql
INSERT INTO attorney_profiles (url, name, ...) VALUES (?, ?, ...)
ON CONFLICT (url) DO UPDATE SET
  name = COALESCE(EXCLUDED.name, attorney_profiles.name),
  bio = COALESCE(EXCLUDED.bio, attorney_profiles.bio),
  ...
  updated_at = NOW();
```

Using `COALESCE` so a detail-page parse enriches (doesn't overwrite with nulls) what the listing-page parse already stored.

---

## 6. Fetcher Configuration

```yaml
# application.yml
crawler:
  fetcher:
    delay-ms: 1000                    # milliseconds between requests
    timeout-ms: 10000                 # HTTP request timeout
    user-agent: "CrawlerPOC/1.0"
    max-retries: 2
  max-detail-pages: 160              # cap on detail page URLs produced
  base-url: "https://www.forthepeople.com"
```

---

## 7. Parser Strategy Dispatch

```
ParserListener receives RawPage
    │
    ├── pageType == LISTING
    │       └── ListingPageParser
    │             • Parse JSON from <script data-drupal-selector="drupal-settings-json">
    │             • Extract attorneys_master_list.data array
    │             • For each entry: build AttorneyProfile (listing-level)
    │             • For first N entries: build CrawlUrl (detail page)
    │
    └── pageType == DETAIL
            └── DetailPageParser
                  • Jsoup selectors (see Section 1)
                  • Build AttorneyProfile (full)
```

### ListingPageParser — Extraction Steps

1. Jsoup: `doc.select("script[data-drupal-selector=drupal-settings-json]").first().data()`
2. Jackson: parse JSON string → navigate to `attorneys_master_list.data`
3. Map each JSON object to `AttorneyProfile` record (listing-level fields only)
4. Generate `CrawlUrl` for each attorney's `url` field (prepend base URL)

### DetailPageParser — Extraction Steps

1. **Name:** `doc.select("div.attorney-sidebar-info h1").text()`
2. **Title:** `doc.select("div.attorney-sidebar-info p.body-r-bold, div.attorney-sidebar-info p.body-md").first().text()`
3. **Office:** `doc.select("div.attorney-office a").text()`
4. **Phone:** `doc.select("div.attorney-phone a[href^=tel]").attr("href")` → strip `tel:`
5. **Email:** `doc.select("div.attorney-email a[href^=mailto]").attr("href")` → strip `mailto:`
6. **Bio:** `doc.select("div.block-attorney-single-body__octane").text()`
7. **Education:** `doc.select("div.educations-list li").eachText()`
8. **Bar Admissions:** `doc.select("div.bar-admission-list li").eachText()`
9. **Memberships:** `doc.select("div.memberships-list li").eachText()`
10. **LinkedIn:** `doc.select("div.attorney-linkedin a").attr("href")`
11. **Facebook:** `doc.select("div.attorney-facebook a").attr("href")`
12. **Twitter/X:** `doc.select("div.attorney-twitter a").attr("href")`
13. **Instagram:** `doc.select("div.attorney-instagram a").attr("href")`
14. **Photo:** `doc.select("div.attorney-sidebar-photo source").first().attr("srcset")` → first URL before space

All selectors return empty string / empty list if the element is not present (Jsoup default behavior). No NPE risk.

---

## 8. Modulith Module Dependencies

```
shared ◀─── scheduler
shared ◀─── fetcher
shared ◀─── parser
shared ◀─── storage
```

No module depends on any other module except `shared`. All inter-module communication goes through Kafka topics. The Modulith verification test confirms this.

---

## 9. Infrastructure (podman-compose.yml)

```yaml
services:
  redpanda:
    image: docker.redpanda.com/redpandadata/redpanda:latest
    command:
      - redpanda start
      - --overprovisioned
      - --smp 1
      - --memory 1G
      - --reserve-memory 0M
      - --node-id 0
      - --kafka-addr PLAINTEXT://0.0.0.0:29092,OUTSIDE://0.0.0.0:9092
      - --advertise-kafka-addr PLAINTEXT://redpanda:29092,OUTSIDE://localhost:9092
    ports:
      - "9092:9092"
      - "8081:8081"   # Schema Registry (not used in POC but available)
      - "8082:8082"   # REST Proxy
      - "9644:9644"   # Admin API

  postgres:
    image: postgres:17
    environment:
      POSTGRES_DB: crawler
      POSTGRES_USER: crawler
      POSTGRES_PASSWORD: crawler
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data

volumes:
  pgdata:
```

---

## 10. Error Handling

Errors are published to the `errors` topic as `CrawlError` records:

| Stage   | Error Case                        | Action                              |
|---------|-----------------------------------|-------------------------------------|
| Fetcher | HTTP 4xx/5xx                      | Produce CrawlError, skip URL        |
| Fetcher | Connection timeout                | Retry up to `max-retries`, then error |
| Fetcher | URL already seen                  | Skip silently (not an error)        |
| Parser  | Missing expected HTML element     | Use empty/null, log warning         |
| Parser  | JSON parse failure (listing page) | Produce CrawlError                  |
| Parser  | Unexpected page structure         | Produce CrawlError                  |
| Storage | DB write failure                  | Log error, message stays uncommitted (Kafka retry) |

For the POC, the `errors` topic is a dead letter queue — log and move on. No re-enqueue / retry from the errors topic.
