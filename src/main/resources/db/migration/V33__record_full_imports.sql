-- Whether a collection imported every offer (new products included) or only refreshed known products.
-- Runs recorded before this column are unknown, so every market gets a full import on the next start.
ALTER TABLE collection_runs ADD COLUMN full_import BOOLEAN NOT NULL DEFAULT FALSE;
