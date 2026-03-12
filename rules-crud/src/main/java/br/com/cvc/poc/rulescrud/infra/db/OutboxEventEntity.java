package br.com.cvc.poc.rulescrud.infra.db;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name="outbox_events")
public class OutboxEventEntity extends PanacheEntityBase {
  @Id
  public UUID id;

  @Column(name="aggregate_id", nullable=false)
  public UUID aggregateId;

  @Column(name="event_type", nullable=false)
  public String eventType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
  public String payload;

  @Column(name="created_at", nullable=false)
  public OffsetDateTime createdAt;

  @Column(name="published_at")
  public OffsetDateTime publishedAt;

  public static OutboxEventEntity create(UUID aggregateId, String eventType, String payload) {
    var e = new OutboxEventEntity();
    e.id = UUID.randomUUID();
    e.aggregateId = aggregateId;
    e.eventType = eventType;
    e.payload = payload;
    e.createdAt = OffsetDateTime.now();
    return e;
  }
}
