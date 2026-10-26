ALTER TABLE organization ADD COLUMN auto_publish boolean NOT NULL DEFAULT false;

CREATE TABLE event (
                       id uuid PRIMARY KEY,
                       organization_id uuid NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
                       title varchar(200) NOT NULL,
                       description text,
                       starts_at timestamptz NOT NULL,
                       location varchar(300),
                       original_url varchar(2048),
                       source_id uuid,
                       external_post_id varchar(200),
                       status varchar(20) NOT NULL
                           CHECK (status IN ('PENDING','PUBLISHED','UNPUBLISHED','REJECTED')),
                       verified boolean NOT NULL DEFAULT false,
                       created_at timestamptz NOT NULL DEFAULT now(),
                       updated_at timestamptz NOT NULL DEFAULT now(),
                       CONSTRAINT event_source_post_key UNIQUE (source_id, external_post_id)
);

CREATE INDEX event_org_idx ON event (organization_id);
CREATE INDEX event_digest_idx ON event (status, starts_at);

ALTER TABLE organization ENABLE ROW LEVEL SECURITY;
ALTER TABLE event ENABLE ROW LEVEL SECURITY;