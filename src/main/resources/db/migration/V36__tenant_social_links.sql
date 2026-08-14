-- Optional social/contact links for the public site's side menu (Instagram/Facebook icons) and an
-- embedded Instagram feed widget (the tenant picks whichever third-party provider they like —
-- Trustindex/SnapWidget/Elfsight/etc. — and pastes that provider's own embed script URL here;
-- this app has no opinion on which provider, it just carries the URL through to the public page).
ALTER TABLE tenants ADD COLUMN instagram_url VARCHAR(500);
ALTER TABLE tenants ADD COLUMN facebook_url VARCHAR(500);
ALTER TABLE tenants ADD COLUMN instagram_feed_url VARCHAR(500);
