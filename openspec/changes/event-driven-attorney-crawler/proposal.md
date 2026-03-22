# Event-Driven Attorney Crawler POC

## Summary

Build an event-driven web crawler that scrapes attorney profiles from [forthepeople.com/attorneys/](https://www.forthepeople.com/attorneys/) using a producer/consumer pipeline backed by Redpanda (Kafka-compatible). The application is structured as a Spring Boot Modulith where each stage (scheduling, fetching, parsing, storage) is an independent module communicating exclusively through Kafka topics.

## Motivation

Demonstrate an event-driven crawling architecture where:

- Each pipeline stage is decoupled and independently scalable
- Data is replayable (re-run parsing without re-crawling by resetting consumer offsets)
- Backpressure is handled naturally by topic buffering
- Faults are isolated (a broken parser doesn't crash the fetcher)

## Target Site

**Morgan & Morgan Attorney Directory** — `https://www.forthepeople.com/attorneys/`

- 69 paginated listing pages (server-rendered HTML, no JS required)
- ~1,000+ individual attorney detail pages at `/attorneys/{slug}/`
- POC scope: **pages 0–9** (~160 attorney profiles)

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Spring Boot App                       │
│                  (Spring Modulith)                       │
│                                                         │
│  scheduler ──KafkaTemplate──▶ urls_to_crawl (topic)     │
│                                      │                  │
│  fetcher ◀──@KafkaListener───────────┘                  │
│     │── checks seen_urls (Postgres)                     │
│     │── HTTP GET + configurable delay                   │
│     └──KafkaTemplate──▶ raw_pages (topic)               │
│                               │                         │
│  parser ◀──@KafkaListener─────┘                         │
│     │── Jsoup + Strategy pattern                        │
│     │── ListingPageParser / DetailPageParser             │
│     ├──KafkaTemplate──▶ parsed_items (topic)            │
│     └──KafkaTemplate──▶ urls_to_crawl (detail URLs)     │
│                               │                         │
│  storage ◀──@KafkaListener────┘                         │
│     └── JPA → Postgres                                  │
│                                                         │
│  shared — Java records, enums (shared kernel)           │
└─────────────────────────────────────────────────────────┘

External services (podman-compose):
  - Redpanda  (:9092)
  - Postgres  (:5432)
```

## Tech Stack

| Concern          | Choice                          | Rationale                                      |
|------------------|---------------------------------|------------------------------------------------|
| Language         | Java 21                         | Records, virtual threads, pattern matching     |
| Framework        | Spring Boot 3.x                 | Ecosystem, Kafka integration                   |
| Modularity       | Spring Modulith (convention)    | Package-based module detection, no annotations |
| Build            | Gradle (Kotlin DSL)             | User preference                                |
| Kafka client     | Spring Kafka                    | KafkaTemplate + @KafkaListener                 |
| Broker           | Redpanda                        | Kafka-compatible, no ZooKeeper, fast local dev |
| HTML parsing     | Jsoup                           | Java equivalent of BeautifulSoup               |
| HTTP client      | RestClient (Spring 6.1+)        | Modern, synchronous, sufficient for POC        |
| Database         | Postgres + Spring Data JPA      | Durable storage + dedup table                  |
| Migrations       | Flyway                          | Spring Boot native integration                 |
| Serialization    | Jackson JSON                    | Spring Kafka default, works with Java records  |
| Containers       | Podman Compose                  | Redpanda + Postgres                            |

## Topics (Kafka)

| Topic            | Producer       | Consumer       | Message Type      |
|------------------|----------------|----------------|-------------------|
| urls_to_crawl    | scheduler, parser | fetcher     | CrawlUrl          |
| raw_pages        | fetcher        | parser         | RawPage           |
| parsed_items     | parser         | storage        | AttorneyProfile   |
| errors           | fetcher, parser | (logging)     | CrawlError        |

## Module Structure

```
com.example.crawler
├── CrawlerApplication.java
├── scheduler/          # Seeds listing page URLs on startup (ApplicationRunner)
├── fetcher/            # Consumes urls_to_crawl, fetches HTML, produces raw_pages
├── parser/             # Consumes raw_pages, parses with Jsoup, produces parsed_items
├── storage/            # Consumes parsed_items, writes to Postgres via JPA
└── shared/             # Records: CrawlUrl, RawPage, AttorneyProfile, CrawlError, PageType
```

Convention-based Modulith: each direct sub-package of `com.example.crawler` is a module.

## Key Design Decisions

1. **Direct Spring Kafka (not Modulith event externalization)** — Each module's contract is the Kafka topic. Clearer boundaries, independent replayability, simpler mental model.

2. **Dedup via Postgres `seen_urls` table** — Fetcher checks before making HTTP calls. Durable across restarts. Simple `INSERT ... ON CONFLICT DO NOTHING` pattern.

3. **Configurable fetch delay (`application.yml`)** — Politeness control to avoid getting blocked. Default ~500ms–1s between requests.

4. **Scheduler as ApplicationRunner** — Seeds listing page URLs (pages 0–9) on startup. No REST endpoint needed for POC.

5. **Parser Strategy pattern** — `ListingPageParser` extracts attorney cards + detail URLs. `DetailPageParser` extracts full attorney profiles. Dispatched by URL pattern or `PageType` enum carried through the pipeline.

6. **Virtual threads enabled** — `spring.threads.virtual.enabled=true`. Fetcher benefits from lightweight I/O threads.

7. **Depth tracking** — `depth` field carried through `CrawlUrl` → `RawPage`. Listing pages are depth 0, detail pages are depth 1. Parser stops producing new URLs at max depth.

## Data Extracted Per Attorney

From detail pages:
- Full name, title/role
- Office location, phone, email
- Photo URL
- Bio text
- Education (list)
- Bar admissions (list)
- Memberships (list)
- Social media links (LinkedIn, Facebook, X, Instagram)

## Non-Goals (POC)

- Playwright / JS rendering
- Distributed workers / multi-instance deployment
- Schema registry (Avro/Protobuf)
- Complex scheduling (cron, priority queues)
- Kubernetes / cloud deployment
- Rate limiting beyond a simple delay
- Authentication / login-gated pages
- Robots.txt enforcement (POC only)

## Scope

- 10 listing pages (pages 0–9)
- ~160 attorney detail pages
- 4 Kafka topics
- 5 Modulith modules (scheduler, fetcher, parser, storage, shared)
- 1 Postgres table for attorney profiles, 1 for seen_urls
- 1 podman-compose.yml (Redpanda + Postgres)
- Modulith verification test
