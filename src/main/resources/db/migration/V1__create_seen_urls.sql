CREATE TABLE seen_urls (
    url       TEXT PRIMARY KEY,
    seen_at   TIMESTAMP NOT NULL DEFAULT NOW()
);
