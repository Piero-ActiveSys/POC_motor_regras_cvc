CREATE TABLE rulesets (
  id            UUID PRIMARY KEY,
  name          TEXT NOT NULL,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by    TEXT NOT NULL
);

CREATE TABLE rules (
  id            UUID PRIMARY KEY,
  ruleset_id    UUID NOT NULL REFERENCES rulesets(id),
  peso          INT  NOT NULL,
  rule_type     TEXT NOT NULL CHECK (rule_type IN ('MARKUP','COMMISSION')),
  enabled       BOOLEAN NOT NULL DEFAULT TRUE,
  conditions_json JSONB NOT NULL,
  value         NUMERIC(18,6) NOT NULL,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by    TEXT NOT NULL,
  updated_at    TIMESTAMPTZ
);

-- 1 regra por peso por ruleset e por tipo
CREATE UNIQUE INDEX ux_rules_ruleset_peso_type ON rules (ruleset_id, peso, rule_type);

CREATE TABLE ruleset_versions (
  id            UUID PRIMARY KEY,
  ruleset_id    UUID NOT NULL REFERENCES rulesets(id),
  version       BIGINT NOT NULL,
  checksum      TEXT NOT NULL,
  published_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  drl           TEXT NOT NULL,
  canonical_json JSONB NOT NULL,
  UNIQUE (ruleset_id, version)
);

CREATE TABLE outbox_events (
  id            UUID PRIMARY KEY,
  aggregate_id  UUID NOT NULL,
  event_type    TEXT NOT NULL,
  payload       JSONB NOT NULL,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  published_at  TIMESTAMPTZ
);

CREATE INDEX idx_outbox_unpublished ON outbox_events (published_at) WHERE published_at IS NULL;
