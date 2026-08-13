-- Negotiated monthly price for a specific tenant, overriding PlanTier.monthlyPrice for MRR/billing
-- reporting only — does not affect what MercadoPago actually charges at checkout.
ALTER TABLE tenants ADD COLUMN custom_monthly_price NUMERIC(10,2);

-- Triage priority for the support inbox, independent of SupportReportType (bug vs. plan-upgrade).
ALTER TABLE support_reports ADD COLUMN priority VARCHAR(20) NOT NULL DEFAULT 'MEDIA';
