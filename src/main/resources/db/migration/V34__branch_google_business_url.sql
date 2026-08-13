-- Optional Google Business Profile link per branch, surfaced on the public site for SEO/trust
-- (a client can jump straight to the branch's reviews/map listing). Per-branch, not per-tenant,
-- since a multi-branch tenant already manages hours/phone/address at the branch level and each
-- branch is realistically its own separate Google Business listing.
ALTER TABLE branches ADD COLUMN google_business_url VARCHAR(500);
