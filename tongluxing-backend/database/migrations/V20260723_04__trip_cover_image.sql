ALTER TABLE trip
  ADD COLUMN IF NOT EXISTS cover_image_key VARCHAR(512) NULL AFTER description;

ALTER TABLE trip_draft
  ADD COLUMN IF NOT EXISTS cover_image_key VARCHAR(512) NULL AFTER description;
