package br.com.cvc.poc.rulescrud.infra.db;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name="rulesets")
public class RulesetEntity extends PanacheEntityBase {
  @Id
  public UUID id;

  @Column(nullable=false)
  public String name;

  @Column(name="created_at", nullable=false)
  public OffsetDateTime createdAt;

  @Column(name="created_by", nullable=false)
  public String createdBy;

  public static RulesetEntity create(String name, String createdBy) {
    var e = new RulesetEntity();
    e.id = UUID.randomUUID();
    e.name = name;
    e.createdBy = createdBy;
    e.createdAt = OffsetDateTime.now();
    return e;
  }
}
