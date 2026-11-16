-- Дошка оголошень (FR-043–FR-053): оголошення й відгуки на них.

CREATE TABLE advert (
    id           uuid          PRIMARY KEY,
    author_id    uuid          NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    type         varchar(20)   NOT NULL CHECK (type IN ('TEAM', 'LEISURE')),
    title        varchar(200)  NOT NULL,
    description  text          NOT NULL,
    slots        integer       NOT NULL CHECK (slots > 0),
    deadline     timestamptz   NOT NULL,
    -- персональні дані (NFR-009): віддається лише автору й підтвердженим відгукувачам
    contact      varchar(200)  NOT NULL,
    status       varchar(20)   NOT NULL CHECK (status IN ('ACTIVE', 'CLOSED', 'DELETED')),
    created_at   timestamptz   NOT NULL DEFAULT now(),
    updated_at   timestamptz   NOT NULL DEFAULT now()
);
-- список активних і закриття за дедлайном (FR-045, FR-050)
CREATE INDEX advert_status_deadline_idx ON advert (status, deadline);
CREATE INDEX advert_author_idx ON advert (author_id);

CREATE TABLE advert_response (
    id          uuid          PRIMARY KEY,
    advert_id   uuid          NOT NULL REFERENCES advert(id) ON DELETE CASCADE,
    user_id     uuid          NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    message     varchar(500),
    status      varchar(20)   NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED', 'WITHDRAWN')),
    created_at  timestamptz   NOT NULL DEFAULT now(),
    decided_at  timestamptz,
    -- один відгук на оголошення (FR-046); відкликаний відгук оновлюється, а не створюється заново
    CONSTRAINT advert_response_once UNIQUE (advert_id, user_id)
);
CREATE INDEX advert_response_user_idx ON advert_response (user_id);

ALTER TABLE advert          ENABLE ROW LEVEL SECURITY;
ALTER TABLE advert_response ENABLE ROW LEVEL SECURITY;
