-- Add status column to track URL lifecycle (SCHEDULED, FETCHED, PROCESSED, FAILED)
ALTER TABLE seen_urls ADD COLUMN status TEXT NOT NULL DEFAULT 'SCHEDULED';

CREATE INDEX idx_seen_urls_status ON seen_urls(status);
