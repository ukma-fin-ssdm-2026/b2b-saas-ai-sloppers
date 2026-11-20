-- Рейтинги (FR-061–FR-064): оцінка автора оголошення його учасниками і оцінка організації.
-- Оцінки зберігаються окремими рядками, а не агрегованим полем: треба знати, хто вже оцінив
-- («один раз» для автора) і чию оцінку змінити (організація). Середнє рахується запитом.

-- Оцінка автора підтвердженим учасником закритого оголошення (FR-061). author_id дублює advert.author_id,
-- щоб середнє автора (FR-062) рахувалося без join до advert.
CREATE TABLE author_rating (
    id         uuid        PRIMARY KEY,
    rater_id   uuid        NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    author_id  uuid        NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    advert_id  uuid        NOT NULL REFERENCES advert(id) ON DELETE CASCADE,
    score      smallint    NOT NULL CHECK (score BETWEEN 1 AND 5),
    created_at timestamptz NOT NULL DEFAULT now(),
    -- один раз за оголошення: повторна спроба — ON CONFLICT DO NOTHING і 409 у сервісі
    CONSTRAINT author_rating_once UNIQUE (rater_id, advert_id),
    CONSTRAINT author_rating_not_self CHECK (rater_id <> author_id)
);
CREATE INDEX author_rating_author_idx ON author_rating (author_id);

-- Оцінка організації користувачем (FR-063): одна на пару, змінюється тим самим рядком.
CREATE TABLE organization_rating (
    user_id         uuid        NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    organization_id uuid        NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    score           smallint    NOT NULL CHECK (score BETWEEN 1 AND 5),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, organization_id)
);
-- середнє для каталогу (FR-064)
CREATE INDEX organization_rating_org_idx ON organization_rating (organization_id);

ALTER TABLE author_rating       ENABLE ROW LEVEL SECURITY;
ALTER TABLE organization_rating ENABLE ROW LEVEL SECURITY;
