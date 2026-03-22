# Tasks — Event-Driven Attorney Crawler POC

Tasks are ordered so each module can be built, tested, and verified before moving to the next. Each phase has a clear "done" criteria.

---

## Phase 0: Project Scaffold & Infrastructure

- [x] **0.1 — Initialize Gradle project with Spring Boot**
  - Spring Boot 3.x, Java 21, Kotlin DSL (`build.gradle.kts`)
  - Dependencies: spring-boot-starter-web, spring-kafka, spring-boot-starter-data-jpa, spring-modulith-starter, flyway-core, jsoup, postgresql driver, jackson
  - Test dependencies: spring-boot-starter-test, spring-modulith-test, spring-kafka-test, testcontainers (kafka + postgres)
  - Spotless plugin for code formatting (see 0.4)
  - `application.yml` with Kafka broker, Postgres connection, virtual threads enabled, fetcher delay config

- [x] **0.2 — podman-compose.yml**
  - Redpanda (port 9092), Postgres (port 5432)
  - Topic auto-creation enabled via Redpanda defaults (no init script needed — Spring Kafka auto-creates topics on first produce)

- [x] **0.3 — Flyway migrations**
  - `V1__create_seen_urls.sql` — `seen_urls` table (`url TEXT PRIMARY KEY, seen_at TIMESTAMP`)
  - `V2__create_attorney_profiles.sql` — `attorney_profiles` table with JSONB columns for lists/maps (see design.md Section 5)

- [x] **0.4 — Spotless code formatter**
  - Add `com.diffplug.spotless` Gradle plugin
  - Configure for Java: Google Java Format (AOSP style — 4-space indent), remove unused imports, trim trailing whitespace, ensure newline at end of file
  - `./gradlew spotlessCheck` — verify formatting (CI-friendly)
  - `./gradlew spotlessApply` — auto-format all sources
  - Wire `spotlessCheck` to run before `compileJava` so builds fail on unformatted code

> **Done when:** `podman compose up` starts Redpanda + Postgres, app boots with `./gradlew bootRun`, Flyway runs migrations, `./gradlew spotlessCheck` passes.

- [ ] **CHECKPOINT 0 — Verify scaffold** *(pause for manual testing)*
  - `podman compose up -d` → Redpanda + Postgres healthy
  - `./gradlew bootRun` → app starts, Flyway migrations run, no errors
  - `./gradlew spotlessCheck` → passes
  - Connect to Postgres and verify tables exist

---

## Phase 1: Shared Module

- [ ] **1.1 — Message records**
  - `CrawlUrl(String url, String source, int depth, PageType pageType)`
  - `RawPage(String url, int status, String html, String source, int depth, PageType pageType, Instant fetchedAt)`
  - `AttorneyProfile(String url, String name, String surname, String title, String location, String phone, String email, String photoUrl, String bio, List<String> practiceAreas, List<String> education, List<String> barAdmissions, List<String> memberships, Map<String, String> socialLinks, List<OfficeLocation> officeLocations, String source, Instant extractedAt)`
  - `OfficeLocation(String state, String city, String abbreviation)`
  - `CrawlError(String url, String stage, String error, Instant occurredAt)`
  - `PageType` enum: `LISTING`, `DETAIL`
  - `Topics` constants class: `URLS_TO_CRAWL`, `RAW_PAGES`, `PARSED_ITEMS`, `ERRORS`

- [ ] **1.2 — Kafka serialization config**
  - Jackson-based `JsonSerializer` / `JsonDeserializer` configured for the record types
  - `spring.json.trusted.packages` set to `com.example.crawler.shared`

- [ ] **1.3 — Unit tests for shared records**
  - Records serialize/deserialize correctly via Jackson round-trip
  - No Kafka or infrastructure needed

> **Done when:** All message types compile, serialize, and deserialize. No infrastructure dependency.

- [ ] **CHECKPOINT 1 — Verify shared module** *(pause for manual testing)*
  - `./gradlew test` → shared record serialization tests pass
  - `./gradlew spotlessCheck` → passes

---

## Phase 2: Scheduler Module

- [ ] **2.1 — SchedulerRunner (ApplicationRunner)**
  - On startup, produces **1** `CrawlUrl` message to `urls_to_crawl`
  - URL: `https://www.forthepeople.com/attorneys/` (single page — all attorneys are in a JSON blob, pagination is client-side)
  - Sets `source = "forthepeople"`, `depth = 0`, `pageType = LISTING`

- [ ] **2.2 — Scheduler integration test**
  - Embedded Kafka (spring-kafka-test)
  - Verify exactly 1 message lands on `urls_to_crawl` topic
  - Assert message content: correct URL, `depth=0`, `pageType=LISTING`

> **Done when:** Scheduler produces exactly 1 well-formed CrawlUrl message on startup.

- [ ] **CHECKPOINT 2 — Verify scheduler** *(pause for manual testing)*
  - `./gradlew test` → scheduler integration test passes
  - `./gradlew bootRun` → observe log: "Seeded 1 URL to urls_to_crawl"
  - Optionally inspect `urls_to_crawl` topic via `rpk topic consume urls_to_crawl`

---

## Phase 3: Fetcher Module

- [ ] **3.1 — SeenUrlRepository**
  - Spring Data JPA repository for `seen_urls` table
  - JPA entity: `SeenUrlEntity` with `url` (PK) and `seenAt`
  - `existsByUrl(String url)` check
  - Save-or-ignore semantics (catch `DataIntegrityViolationException` or use native query `INSERT ... ON CONFLICT DO NOTHING`)

- [ ] **3.2 — FetcherService**
  - Uses `RestClient` (Spring 6.1+) to GET a URL
  - Configurable via `application.yml`:
    - `crawler.fetcher.delay-ms` (default: 1000) — delay between requests
    - `crawler.fetcher.timeout-ms` (default: 10000) — HTTP timeout
    - `crawler.fetcher.user-agent` (default: "CrawlerPOC/1.0")
    - `crawler.fetcher.max-retries` (default: 2)
  - Returns response body (HTML string) + HTTP status code
  - Retries on connection errors up to `max-retries`

- [ ] **3.3 — FetcherListener (@KafkaListener on urls_to_crawl)**
  - Consumer group: `crawler-fetcher`
  - Checks `seen_urls` — skips if already fetched
  - Calls FetcherService to fetch HTML
  - Marks URL as seen in Postgres
  - Produces `RawPage` to `raw_pages` topic (carries forward `depth`, `pageType`, `source`)
  - On error, produces `CrawlError` to `errors` topic

- [ ] **3.4 — Fetcher integration test**
  - Embedded Kafka + Testcontainers Postgres
  - Produce a `CrawlUrl` to `urls_to_crawl` → verify `RawPage` on `raw_pages` with `status=200` and non-empty HTML
  - Produce the same URL again → verify it's skipped (dedup)
  - Produce a bad URL → verify `CrawlError` on `errors` topic

> **Done when:** Fetcher consumes URLs, fetches HTML, deduplicates, and handles errors.

- [ ] **CHECKPOINT 3 — Verify fetcher** *(pause for manual testing)*
  - `./gradlew test` → fetcher integration test passes
  - `./gradlew bootRun` → scheduler seeds URL → fetcher picks it up → `RawPage` produced to `raw_pages`
  - Verify `seen_urls` table has the fetched URL
  - Optionally inspect `raw_pages` topic via `rpk topic consume raw_pages --num 1`

---

## Phase 4: Parser Module

- [ ] **4.1 — ListingPageParser**
  - Input: HTML of `/attorneys/` (full page including embedded JSON)
  - Extraction: Jsoup `doc.select("script[data-drupal-selector=drupal-settings-json]").first().data()`
  - Jackson: parse JSON → navigate to `attorneys_master_list.data` (array of ~1,100 objects)
  - Each JSON object has: `id`, `name`, `surName`, `officeLocations[]`, `offices[]`, `photo`, `practiceAreas[]`, `url`
  - Map each to `AttorneyProfile` (listing-level — no bio, education, bar, memberships, socials)
  - For the first `crawler.max-detail-pages` entries (default: 160), produce `CrawlUrl` with `depth=1`, `pageType=DETAIL`
  - Returns: list of `AttorneyProfile` (listing-level) + list of `CrawlUrl` (detail page URLs, capped)

- [ ] **4.2 — DetailPageParser**
  - Input: HTML of `/attorneys/{slug}/`
  - Jsoup selectors (see design.md Section 7):
    - Name: `div.attorney-sidebar-info h1` → `.text()`
    - Title: `div.attorney-sidebar-info p.body-r-bold, div.attorney-sidebar-info p.body-md` → first match `.text()`
    - Office: `div.attorney-office a` → `.text()`
    - Phone: `div.attorney-phone a[href^=tel]` → `.attr("href")` strip `tel:`
    - Email: `div.attorney-email a[href^=mailto]` → `.attr("href")` strip `mailto:`
    - Bio: `div.block-attorney-single-body__octane` → `.text()`
    - Education: `div.educations-list li` → `.eachText()`
    - Bar Admissions: `div.bar-admission-list li` → `.eachText()`
    - Memberships: `div.memberships-list li` → `.eachText()`
    - LinkedIn: `div.attorney-linkedin a` → `.attr("href")`
    - Facebook: `div.attorney-facebook a` → `.attr("href")`
    - Twitter/X: `div.attorney-twitter a` → `.attr("href")`
    - Instagram: `div.attorney-instagram a` → `.attr("href")`
    - Photo: `div.attorney-sidebar-photo source:first-of-type` → `.attr("srcset")` first URL
  - All selectors return empty string / empty list when element is absent
  - Returns: single `AttorneyProfile` (complete)

- [ ] **4.3 — ParserListener (@KafkaListener on raw_pages)**
  - Consumer group: `crawler-parser`
  - Dispatches to `ListingPageParser` or `DetailPageParser` based on `RawPage.pageType()`
  - Produces `AttorneyProfile` messages to `parsed_items`
  - Produces discovered `CrawlUrl` messages (detail page URLs) to `urls_to_crawl`
  - On parse error, produces `CrawlError` to `errors`

- [ ] **4.4 — Parser unit tests**
  - Save HTML fixtures: listing page snapshot, 2 detail page snapshots (rich + minimal profile)
  - Feed fixtures to each parser, assert extracted fields match expected values
  - No Kafka, no HTTP

- [ ] **4.5 — Parser integration test**
  - Produce a `RawPage` with fixture HTML to `raw_pages`
  - Verify `AttorneyProfile` on `parsed_items` and `CrawlUrl` messages on `urls_to_crawl`

> **Done when:** Both parsers correctly extract data from HTML fixtures. Listener routes correctly and produces to the right topics.

- [ ] **CHECKPOINT 4 — Verify parser** *(pause for manual testing)*
  - `./gradlew test` → parser unit + integration tests pass
  - `./gradlew bootRun` → full pipeline up to parser: scheduler → fetcher → parser → `parsed_items` populated
  - Optionally inspect `parsed_items` topic to see attorney profiles
  - Verify detail page `CrawlUrl` messages appear on `urls_to_crawl`

---

## Phase 5: Storage Module

- [ ] **5.1 — AttorneyProfileEntity (JPA entity)**
  - Maps to `attorney_profiles` table
  - All fields from the `AttorneyProfile` record
  - `url` as natural key (`@Column(unique = true)`)
  - List/Map fields stored as JSONB (use `@JdbcTypeCode(SqlTypes.JSON)` or Hibernate JSON type)
  - `createdAt`, `updatedAt` timestamps

- [ ] **5.2 — AttorneyProfileRepository**
  - Spring Data JPA repository
  - Custom upsert method: if URL exists, update non-null fields (COALESCE semantics — detail enriches listing data, doesn't overwrite with nulls)
  - Can use `@Query` with native upsert or handle in service layer with find-then-merge

- [ ] **5.3 — StorageListener (@KafkaListener on parsed_items)**
  - Consumer group: `crawler-storage`
  - Consumes `AttorneyProfile` messages
  - Maps record → entity
  - Persists via repository (upsert)

- [ ] **5.4 — Storage integration test**
  - Embedded Kafka + Testcontainers Postgres
  - Produce an `AttorneyProfile` (listing-level, no bio) to `parsed_items` → verify row created
  - Produce an `AttorneyProfile` (detail-level, with bio) for the same URL → verify row updated, bio populated, listing-level fields preserved
  - Verify no duplicate rows

> **Done when:** Storage consumes profiles and persists them correctly with upsert/enrichment behavior.

- [ ] **CHECKPOINT 5 — Verify storage** *(pause for manual testing)*
  - `./gradlew test` → storage integration test passes
  - `./gradlew bootRun` → full pipeline end-to-end: attorneys land in Postgres
  - `SELECT count(*) FROM attorney_profiles;` → rows present
  - Spot-check a few rows for completeness (bio, education, etc.)

---

## Phase 6: Integration & Verification

- [ ] **6.1 — Modulith verification test**
  - `ApplicationModules(CrawlerApplication.class).verify()`
  - Confirms no illegal cross-module dependencies (all modules depend only on `shared`)

- [ ] **6.2 — End-to-end smoke test**
  - Testcontainers: Redpanda + Postgres
  - Set `crawler.max-detail-pages=5` to keep test fast
  - Trigger scheduler → wait for pipeline to complete (poll `attorney_profiles` table)
  - Assert: 1,100 listing-level rows + 5 detail-enriched rows in DB
  - Assert: detail-enriched rows have bio, education, etc. populated

- [ ] **6.3 — Final spotlessApply + spotlessCheck**
  - Run `./gradlew spotlessApply` to format all code
  - Run `./gradlew spotlessCheck` to verify
  - Run full `./gradlew build` (compile + test + check)

> **Done when:** Full pipeline runs end-to-end. Modulith structure verified. All tests pass. Code formatted.
