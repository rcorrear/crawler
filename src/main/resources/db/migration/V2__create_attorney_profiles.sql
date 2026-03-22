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

CREATE INDEX idx_attorney_profiles_url ON attorney_profiles (url);
