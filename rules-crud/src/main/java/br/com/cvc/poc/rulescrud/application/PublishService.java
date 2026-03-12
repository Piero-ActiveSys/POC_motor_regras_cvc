package br.com.cvc.poc.rulescrud.application;

import br.com.cvc.poc.contracts.RuleDefinition;
import br.com.cvc.poc.contracts.RulesetUpdateEvent;
import br.com.cvc.poc.rulescrud.infra.db.OutboxEventEntity;
import br.com.cvc.poc.rulescrud.infra.db.RuleEntity;
import br.com.cvc.poc.rulescrud.infra.db.RulesetVersionEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PublishService {

  @Inject RuleCanonicalizer canonicalizer;
  @Inject ObjectMapper mapper;

  @Transactional
  public RulesetVersionEntity publish(UUID rulesetId, String publishedBy) {
    // Canonical rules ordered by peso asc then type
    List<RuleEntity> entities = RuleEntity.list("rulesetId", rulesetId);
    var defs = entities.stream()
        .map(canonicalizer::toDefinition)
        .sorted(Comparator.comparingInt(RuleDefinition::peso).thenComparing(d -> d.ruleType().name()))
        .toList();

    String drl = DrlBuilder.build(defs);

    // compute checksum from canonical json + drl
    String canonicalJson;
    try { canonicalJson = mapper.writeValueAsString(defs); }
    catch (Exception e) { throw new RuntimeException(e); }

    String checksum = Checksum.sha256(drl + "|" + canonicalJson);

    // version = max(version)+1
    Long last = RulesetVersionEntity.find("rulesetId = ?1 order by version desc", rulesetId)
        .firstResultOptional().map(x -> ((RulesetVersionEntity)x).version).orElse(0L);
    long nextVersion = last + 1;

    var ver = RulesetVersionEntity.create(rulesetId, nextVersion, checksum, drl, canonicalJson);
    ver.persist();

    // outbox event
    var evt = new RulesetUpdateEvent(
        UUID.randomUUID().toString(),
        rulesetId.toString(),
        nextVersion,
        checksum,
        OffsetDateTime.now().toString(),
        new RulesetUpdateEvent.Payload(drl, defs)
    );

    String payload;
    try { payload = mapper.writeValueAsString(evt); }
    catch (Exception e) { throw new RuntimeException(e); }

    var out = OutboxEventEntity.create(rulesetId, "RULESET_PUBLISHED", payload);
    out.persist();

    return ver;
  }
}
