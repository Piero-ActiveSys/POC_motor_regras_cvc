package br.com.cvc.poc.rulescrud.infra.db;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name="rules")
public class RuleEntity extends PanacheEntityBase {
  @Id
  public UUID id;

  @Column(name="ruleset_id", nullable=false)
  public UUID rulesetId;

  @Column(nullable=false)
  public Integer peso;

  @Column(name="rule_type", nullable=false)
  public String ruleType;

  @Column(nullable=false)
  public Boolean enabled;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name="conditions_json", nullable=false, columnDefinition="jsonb")
  public String conditionsJson;

  @Column(nullable=false, precision=18, scale=6)
  public BigDecimal value;

  @Column(name="created_at", nullable=false)
  public OffsetDateTime createdAt;

  @Column(name="created_by", nullable=false)
  public String createdBy;

  @Column(name="updated_at")
  public OffsetDateTime updatedAt;

  public static RuleEntity create(UUID rulesetId, Integer peso, String ruleType, Boolean enabled, String conditionsJson, BigDecimal value, String createdBy) {
    var e = new RuleEntity();
    e.id = UUID.randomUUID();
    e.rulesetId = rulesetId;
    e.peso = peso;
    e.ruleType = ruleType;
    e.enabled = enabled;
    e.conditionsJson = conditionsJson;
    e.value = value;
    e.createdBy = createdBy;
    e.createdAt = OffsetDateTime.now();
    return e;
  }
}
