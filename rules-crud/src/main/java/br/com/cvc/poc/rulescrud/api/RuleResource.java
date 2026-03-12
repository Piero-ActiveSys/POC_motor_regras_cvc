package br.com.cvc.poc.rulescrud.api;

import br.com.cvc.poc.contracts.RuleUpsertRequest;
import br.com.cvc.poc.rulescrud.api.contract.BatchRuleUpsertRequest;
import br.com.cvc.poc.rulescrud.api.contract.BatchRuleUpsertResponse;
import br.com.cvc.poc.rulescrud.application.RuleCanonicalizer;
import br.com.cvc.poc.rulescrud.application.service.RuleBatchUpsertService;
import br.com.cvc.poc.rulescrud.infra.db.RuleEntity;
import br.com.cvc.poc.rulescrud.infra.db.RulesetEntity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Path("/rulesets/{rulesetId}/rules")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RuleResource {
  @Inject RuleCanonicalizer canonicalizer;
  @Inject RuleBatchUpsertService ruleBatchUpsertService;

  @POST
  @Transactional
  public RuleEntity upsert(@PathParam("rulesetId") UUID rulesetId, @Valid RuleUpsertRequest req) {
    var rs = RulesetEntity.findById(rulesetId);
    if (rs == null) throw new NotFoundException("ruleset not found");

    // 1 regra por peso + tipo (unique index). Upsert via find.
    RuleEntity existing = RuleEntity.find("rulesetId = ?1 and peso = ?2 and ruleType = ?3",
        rulesetId, req.peso(), req.ruleType().name()).firstResult();

    var json = canonicalizer.conditionsJson(req);
    if (existing == null) {
      var e = RuleEntity.create(rulesetId, req.peso(), req.ruleType().name(), req.enabled(), json, req.value(), req.createdBy());
      e.persist();
      return e;
    }
    existing.enabled = req.enabled();
    existing.conditionsJson = json;
    existing.value = req.value();
    existing.updatedAt = OffsetDateTime.now();
    return existing;
  }



  @POST
  @Path("/batch")
  @Transactional
  public BatchRuleUpsertResponse upsertBatch(@PathParam("rulesetId") UUID rulesetId, @Valid BatchRuleUpsertRequest req) {
    var rs = RulesetEntity.findById(rulesetId);
    if (rs == null) throw new NotFoundException("ruleset not found");

    long startedAt = System.currentTimeMillis();
    int processed = ruleBatchUpsertService.upsertBatch(rulesetId, req.rules(), req.resolvedChunkSize());
    return new BatchRuleUpsertResponse(
        rulesetId,
        req.rules().size(),
        processed,
        req.resolvedChunkSize(),
        System.currentTimeMillis() - startedAt
    );
  }

  @GET
  public List<RuleEntity> list(@PathParam("rulesetId") UUID rulesetId) {
    return RuleEntity.list("rulesetId", rulesetId);
  }

  @DELETE
  @Path("/{ruleId}")
  @Transactional
  public void delete(@PathParam("rulesetId") UUID rulesetId, @PathParam("ruleId") UUID ruleId) {
    RuleEntity.delete("id = ?1 and rulesetId = ?2", ruleId, rulesetId);
  }
}
