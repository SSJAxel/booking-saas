-- Unifies "Mejorar plan" requests into the same inbox as bug reports, distinguished by type.
ALTER TABLE support_reports
  ADD COLUMN type VARCHAR(20) NOT NULL DEFAULT 'BUG';

-- A plan-upgrade request has no screenshot — only bug reports require one (enforced in
-- SupportReportService, not the DB, since the rule is per-type).
ALTER TABLE support_reports
  ALTER COLUMN image_path DROP NOT NULL,
  ALTER COLUMN image_content_type DROP NOT NULL;
