package br.com.cvc.poc.rulescrud.application.service;

import br.com.cvc.poc.contracts.RuleUpsertRequest;
import br.com.cvc.poc.rulescrud.application.RuleCanonicalizer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class RuleBatchUpsertService {

  private static final String UPSERT_SQL = """
      INSERT INTO rules (
        id, ruleset_id, peso, rule_type, enabled, conditions_json, value, created_at, created_by, updated_at
      ) VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?)
      ON CONFLICT (ruleset_id, peso, rule_type)
      DO UPDATE SET
        enabled = EXCLUDED.enabled,
        conditions_json = EXCLUDED.conditions_json,
        value = EXCLUDED.value,
        updated_at = EXCLUDED.updated_at
      """;

  @Inject EntityManager entityManager;
  @Inject RuleCanonicalizer canonicalizer;

  public int upsertBatch(UUID rulesetId, List<RuleUpsertRequest> rules, int chunkSize) {
    if (rules == null || rules.isEmpty()) {
      return 0;
    }

    final int resolvedChunkSize = Math.max(1, chunkSize);
    final int[] processed = {0};

    entityManager.unwrap(Session.class).doWork(connection -> {
      try (PreparedStatement ps = connection.prepareStatement(UPSERT_SQL)) {
        int pending = 0;

        for (RuleUpsertRequest rule : rules) {
          OffsetDateTime now = OffsetDateTime.now();
          ps.setObject(1, UUID.randomUUID());
          ps.setObject(2, rulesetId);
          ps.setInt(3, rule.peso());
          ps.setString(4, rule.ruleType().name());
          ps.setBoolean(5, rule.enabled());
          ps.setString(6, canonicalizer.conditionsJson(rule));
          ps.setBigDecimal(7, rule.value());
          ps.setTimestamp(8, Timestamp.from(now.toInstant()));
          ps.setString(9, rule.createdBy());
          ps.setTimestamp(10, Timestamp.from(now.toInstant()));
          ps.addBatch();
          pending++;
          processed[0]++;

          if (pending >= resolvedChunkSize) {
            ps.executeBatch();
            pending = 0;
          }
        }

        if (pending > 0) {
          ps.executeBatch();
        }
      }
    });

    entityManager.flush();
    entityManager.clear();
    return processed[0];
  }
}
