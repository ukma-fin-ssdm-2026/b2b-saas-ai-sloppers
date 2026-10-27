-- Посилання на картинки в Supabase Storage (бакет images).
-- Самі файли в базі не зберігаються, лише їхні публічні URL.
ALTER TABLE organization ADD COLUMN logo_url varchar(2048);
ALTER TABLE event        ADD COLUMN image_url varchar(2048);
