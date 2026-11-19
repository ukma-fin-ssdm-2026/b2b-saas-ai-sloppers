-- Модерація користувачів і контенту (FR-054–FR-060): блокування, скарги, автофільтр заборонених слів.

-- Блокування користувача (FR-054). banned_by = null — автоматичне блокування системою (FR-060),
-- expires_at = null — безстрокове. Розблокування не видаляє рядок, а лише заповнює lifted_at;
-- lifted_by = null означає, що строк вийшов і бан зняв планувальник, а не людина.
CREATE TABLE ban (
    id              uuid         PRIMARY KEY,
    user_id         uuid         NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    banned_by       uuid         REFERENCES app_user(id) ON DELETE SET NULL,
    reason          varchar(500) NOT NULL,
    expires_at      timestamptz,
    -- куди повернути app_user.status після розблокування: до бану користувач міг бути ще PENDING
    previous_status varchar(20)  NOT NULL CHECK (previous_status IN ('PENDING', 'ACTIVE')),
    created_at      timestamptz  NOT NULL DEFAULT now(),
    lifted_at       timestamptz,
    lifted_by       uuid         REFERENCES app_user(id) ON DELETE SET NULL
);
-- не більше одного незнятого бану на людину: двічі заблокувати не вийде навіть у гонці
CREATE UNIQUE INDEX ban_one_active_idx ON ban (user_id) WHERE lifted_at IS NULL;
-- вибірка банів, у яких вийшов строк (FR-054, планувальник)
CREATE INDEX ban_expiry_idx ON ban (expires_at) WHERE lifted_at IS NULL AND expires_at IS NOT NULL;

-- Скарга на оголошення або відгук (FR-056). Замість поліморфного target_id — два зовнішні ключі,
-- щоб цілісність гарантувала БД: скарга не може вказувати на об'єкт, якого немає.
CREATE TABLE complaint (
    id          uuid         PRIMARY KEY,
    reporter_id uuid         NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    -- автор оскарженого контенту на момент скарги. Денормалізація: без неї підрахунок «3 скарги від
    -- різних користувачів на контент однієї людини» (FR-060) потребував би join до advert і
    -- advert_response, тобто читання таблиць дошки з модуля модерації
    author_id   uuid         NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    advert_id   uuid         REFERENCES advert(id) ON DELETE CASCADE,
    response_id uuid         REFERENCES advert_response(id) ON DELETE CASCADE,
    category    varchar(20)  NOT NULL CHECK (category IN ('SPAM', 'ABUSE', 'OTHER')),
    comment     varchar(500),
    status      varchar(20)  NOT NULL CHECK (status IN ('OPEN', 'RESOLVED', 'DISMISSED')),
    resolved_by uuid         REFERENCES app_user(id) ON DELETE SET NULL,
    resolution  varchar(500),
    created_at  timestamptz  NOT NULL DEFAULT now(),
    resolved_at timestamptz,
    -- заповнено рівно одне з advert_id, response_id
    CONSTRAINT complaint_one_target CHECK ((advert_id IS NULL) <> (response_id IS NULL))
);
-- одна скарга від користувача на той самий об'єкт (FR-056). Звичайний UNIQUE (reporter_id, advert_id,
-- response_id) тут не працює: у PostgreSQL рядки з NULL у ключі вважаються різними, тож дублікати
-- пройшли б. Два часткові індекси дають потрібне обмеження для кожного типу цілі окремо.
CREATE UNIQUE INDEX complaint_once_advert_idx ON complaint (reporter_id, advert_id)
    WHERE advert_id IS NOT NULL;
CREATE UNIQUE INDEX complaint_once_response_idx ON complaint (reporter_id, response_id)
    WHERE response_id IS NOT NULL;
-- черга скарг для Global Admin, найновіші спершу (FR-057)
CREATE INDEX complaint_queue_idx ON complaint (status, created_at DESC);
-- підрахунок скаржників на контент автора (FR-060); відхилені скарги в ліміт не йдуть
CREATE INDEX complaint_author_idx ON complaint (author_id) WHERE status <> 'DISMISSED';

-- Список заборонених слів для автофільтра, веде Global Admin (FR-058). Слова зберігаються в нижньому
-- регістрі (CHECK), тож UNIQUE працює без урахування регістру — як з email у app_user.
CREATE TABLE banned_word (
    id         uuid         PRIMARY KEY,
    word       varchar(100) NOT NULL UNIQUE CHECK (word = lower(word) AND length(word) > 1),
    added_by   uuid         REFERENCES app_user(id) ON DELETE SET NULL,
    created_at timestamptz  NOT NULL DEFAULT now()
);

-- Журнал відмов автофільтра (FR-058). Потрібен, щоб порахувати «3 відхилені повідомлення за добу»
-- для автоматичного блокування (FR-060): самого відхиленого тексту в системі не залишається.
CREATE TABLE filter_rejection (
    id           uuid         PRIMARY KEY,
    user_id      uuid         NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    target_type  varchar(20)  NOT NULL CHECK (target_type IN ('ADVERT', 'REPLY')),
    matched_word varchar(100) NOT NULL,
    created_at   timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX filter_rejection_user_idx ON filter_rejection (user_id, created_at DESC);

-- Модератор прибирає відгук, на який підтвердили скаргу (FR-057). Для оголошення такий стан уже є
-- (DELETED), для відгуку потрібен окремий: REJECTED — це рішення автора оголошення, не модератора.
ALTER TABLE advert_response DROP CONSTRAINT advert_response_status_check;
ALTER TABLE advert_response ADD CONSTRAINT advert_response_status_check
    CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED', 'WITHDRAWN', 'REMOVED'));

ALTER TABLE ban              ENABLE ROW LEVEL SECURITY;
ALTER TABLE complaint        ENABLE ROW LEVEL SECURITY;
ALTER TABLE banned_word      ENABLE ROW LEVEL SECURITY;
ALTER TABLE filter_rejection ENABLE ROW LEVEL SECURITY;
