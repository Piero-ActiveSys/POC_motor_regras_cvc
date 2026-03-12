package br.com.cvc.poc.rulescrud.infra.kafka;

import br.com.cvc.poc.rulescrud.infra.db.OutboxEventEntity;
import io.smallrye.reactive.messaging.kafka.Record;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import java.time.OffsetDateTime;
import java.util.List;

@ApplicationScoped
public class OutboxPublisher {

  @Inject
  @Channel("ruleset_updates")
  Emitter<Record<String, String>> emitter;

  /**
   * Simple polling publisher (POC).
   * In production, use Debezium/outbox pattern or better scheduling control.
   */
  @Transactional
  @io.quarkus.scheduler.Scheduled(every="1s", delayed="2s")
  void publishPending() {
    List<OutboxEventEntity> pending = OutboxEventEntity.list("publishedAt is null");
    for (var e : pending) {
      emitter.send(Record.of(e.aggregateId.toString(), e.payload));
      e.publishedAt = OffsetDateTime.now();
    }
  }
}
