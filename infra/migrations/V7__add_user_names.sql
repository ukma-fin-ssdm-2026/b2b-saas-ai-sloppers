-- Ім'я та прізвище користувача (FR-069). Необов'язкові: акаунти, створені до V7, заповнюють їх через PATCH /auth/me.

ALTER TABLE app_user
    ADD COLUMN first_name varchar(50),
    ADD COLUMN last_name  varchar(50);
