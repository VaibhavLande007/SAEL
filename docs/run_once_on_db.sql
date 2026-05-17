-- Run this ONCE on your existing Azure PostgreSQL database.
-- Table names are snake_case, column names are camelCase (as Prisma created them).
-- Safe to re-run — all statements use IF NOT EXISTS / WHERE NOT EXISTS.

-- ── Refresh tokens table (not in Prisma schema) ───────────────────────────
CREATE TABLE IF NOT EXISTS refresh_tokens (
    "id"         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    "userId"     UUID        NOT NULL REFERENCES users("id") ON DELETE CASCADE,
    "tokenHash"  TEXT        NOT NULL UNIQUE,
    "expiresAt"  TIMESTAMPTZ NOT NULL,
    "revoked"    BOOLEAN     NOT NULL DEFAULT FALSE,
    "createdAt"  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user ON refresh_tokens("userId") WHERE "revoked" = FALSE;
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_hash ON refresh_tokens("tokenHash");

-- ── Extra columns on notification_recipients ──────────────────────────────
ALTER TABLE notification_recipients ADD COLUMN IF NOT EXISTS "networkId"             UUID    REFERENCES networks("id");
ALTER TABLE notification_recipients ADD COLUMN IF NOT EXISTS "receivesCritical"      BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE notification_recipients ADD COLUMN IF NOT EXISTS "receivesWarning"       BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE notification_recipients ADD COLUMN IF NOT EXISTS "receivesDailySummary"  BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX IF NOT EXISTS idx_notif_recipients_network ON notification_recipients("networkId");

-- ── Seed system roles ─────────────────────────────────────────────────────
INSERT INTO roles ("id", "tenantId", "name", "description", "isSystem")
SELECT gen_random_uuid(), NULL, 'SYSTEM_ADMIN',  'Platform super admin', TRUE
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE "name" = 'SYSTEM_ADMIN'  AND "tenantId" IS NULL);

INSERT INTO roles ("id", "tenantId", "name", "description", "isSystem")
SELECT gen_random_uuid(), NULL, 'NETWORK_ADMIN', 'Network admin', TRUE
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE "name" = 'NETWORK_ADMIN' AND "tenantId" IS NULL);

INSERT INTO roles ("id", "tenantId", "name", "description", "isSystem")
SELECT gen_random_uuid(), NULL, 'LAB_MANAGER',   'Lab manager', TRUE
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE "name" = 'LAB_MANAGER'  AND "tenantId" IS NULL);

INSERT INTO roles ("id", "tenantId", "name", "description", "isSystem")
SELECT gen_random_uuid(), NULL, 'EMBRYOLOGIST',  'Read-only lab user', TRUE
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE "name" = 'EMBRYOLOGIST' AND "tenantId" IS NULL);
