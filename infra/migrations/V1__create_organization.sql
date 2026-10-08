CREATE TABLE organization (
    id               uuid          PRIMARY KEY,
    name             varchar(100)  NOT NULL,
    slug             varchar(50)   NOT NULL,
    description      varchar(2000),
    instagram_handle varchar(30),
    contact_email    varchar(320),
    created_at       timestamptz   NOT NULL DEFAULT now(),
    updated_at       timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT organization_slug_key UNIQUE (slug),
    CONSTRAINT organization_slug_format CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);
