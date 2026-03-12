package br.com.cvc.poc.rulescrud.infra.db;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name="ruleset_versions")
public class RulesetVersionEntity extends PanacheEntityBase {
  @Id
  public UUID id;

  @Column(name="ruleset_id", nullable=false)
  public UUID rulesetId;

  @Column(nullable=false)
  public Long version;

  @Column(nullable=false)
  public String checksum;

  @Column(name="published_at", nullable=false)
  public OffsetDateTime publishedAt;

  @Column(nullable=false, columnDefinition="text")
  public String drl;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name="canonical_json", nullable=false, columnDefinition="jsonb")
  public String canonicalJson;

  public static RulesetVersionEntity create(UUID rulesetId, long version, String checksum, String drl, String canonicalJson) {
    var e = new RulesetVersionEntity();
    e.id = UUID.randomUUID();
    e.rulesetId = rulesetId;
    e.version = version;
    e.checksum = checksum;
    e.drl = drl;
    e.canonicalJson = canonicalJson;
    e.publishedAt = OffsetDateTime.now();
    return e;
  }
}
