-- Auth (FR-001–FR-013): користувачі, голови організацій, журнал змін ролей.

CREATE TABLE app_user (
    id                       uuid          PRIMARY KEY,
    -- зберігаємо в нижньому регістрі, тож UNIQUE працює без урахування регістру
    email                    varchar(320)  NOT NULL,
    -- bcrypt (NFR-008); сам пароль ніде не зберігається
    password_hash            varchar(100)  NOT NULL,
    status                   varchar(20)   NOT NULL CHECK (status IN ('PENDING', 'ACTIVE', 'BLOCKED')),
    global_admin             boolean       NOT NULL DEFAULT false,
    -- код підтвердження пошти (FR-002, FR-003): лише хеш, строк дії, залишок спроб
    verification_code_hash   varchar(100),
    verification_expires_at  timestamptz,
    verification_attempts    integer,
    verification_sent_at     timestamptz,
    created_at               timestamptz   NOT NULL DEFAULT now(),
    updated_at               timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT app_user_email_key UNIQUE (email),
    CONSTRAINT app_user_email_lower CHECK (email = lower(email))
);

-- Хто є головою якої організації (FR-006, FR-008–FR-011)
CREATE TABLE organization_head (
    organization_id  uuid         NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    user_id          uuid         NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    appointed_by     uuid         REFERENCES app_user(id) ON DELETE SET NULL,
    appointed_at     timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, user_id)
);
CREATE INDEX organization_head_user_idx ON organization_head (user_id);

-- Журнал змін ролей (FR-013): хто, кому, що, коли
CREATE TABLE role_change (
    id               uuid         PRIMARY KEY,
    organization_id  uuid         NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    target_user_id   uuid         NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    actor_user_id    uuid         REFERENCES app_user(id) ON DELETE SET NULL,
    action           varchar(20)  NOT NULL CHECK (action IN ('APPOINTED', 'REMOVED')),
    created_at       timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX role_change_org_idx ON role_change (organization_id, created_at DESC);

-- закрити від REST API Supabase, як і решту таблиць
ALTER TABLE app_user          ENABLE ROW LEVEL SECURITY;
ALTER TABLE organization_head ENABLE ROW LEVEL SECURITY;
ALTER TABLE role_change       ENABLE ROW LEVEL SECURITY;
