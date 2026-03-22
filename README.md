# Attorney Crawler POC

Event-driven web crawler that scrapes attorney profiles from [forthepeople.com](https://www.forthepeople.com/attorneys/) using a producer/consumer pipeline backed by Redpanda (Kafka-compatible).

## Architecture

Built with **Java 21 + Spring Boot + Spring Modulith**. Each pipeline stage is an independent module communicating exclusively through Kafka topics:

```
Scheduler → urls_to_crawl → Fetcher → raw_pages → Parser → parsed_items → Storage (Postgres)
```

| Module    | Role                                                        |
|-----------|-------------------------------------------------------------|
| scheduler | Seeds the listing page URL on startup (ApplicationRunner)   |
| fetcher   | Consumes URLs, fetches HTML, deduplicates via Postgres      |
| parser    | Extracts attorney data (JSON from listing, Jsoup for detail)|
| storage   | Persists attorney profiles to Postgres with upsert          |
| shared    | Message records, topic constants (shared kernel)            |

### Topics

| Topic           | Message Type    | Description                    |
|-----------------|-----------------|--------------------------------|
| urls_to_crawl   | CrawlUrl        | URLs to fetch                  |
| raw_pages       | RawPage         | Fetched HTML content           |
| parsed_items    | AttorneyProfile | Extracted attorney data        |
| errors          | CrawlError      | Dead letter queue for failures |

## Tech Stack

- Java 21, Gradle (Kotlin DSL)
- Spring Boot 3.x, Spring Modulith, Spring Kafka, Spring Data JPA
- Redpanda (Kafka-compatible broker)
- Jsoup (HTML parsing)
- PostgreSQL + Flyway
- Testcontainers (integration tests)

## Prerequisites

- Java 21+
- Podman & Podman Compose (or Docker & Docker Compose)

## Running

Start infrastructure:

```bash
podman compose up -d
```

Run the application:

```bash
./gradlew bootRun
```

The crawler will:
1. Seed the listing page URL
2. Fetch the page and extract the embedded JSON (1,100 attorneys)
3. Produce detail page URLs (capped by `crawler.max-detail-pages`)
4. Fetch and parse each detail page
5. Store all profiles in Postgres

## Configuration

Key settings in `application.yml`:

| Property                     | Default | Description                         |
|------------------------------|---------|-------------------------------------|
| `crawler.fetcher.delay-ms`   | 1000    | Delay between HTTP requests (ms)    |
| `crawler.fetcher.timeout-ms` | 10000   | HTTP request timeout (ms)           |
| `crawler.fetcher.max-retries`| 2       | Retry count on connection errors    |
| `crawler.max-detail-pages`   | 160     | Max detail pages to crawl           |

## Testing

```bash
./gradlew test
```

Tests use embedded Kafka and Testcontainers (Postgres) — no external infrastructure needed.
