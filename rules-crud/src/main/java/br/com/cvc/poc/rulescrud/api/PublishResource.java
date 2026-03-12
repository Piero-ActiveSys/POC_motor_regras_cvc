package br.com.cvc.poc.rulescrud.api;

import br.com.cvc.poc.contracts.PublishRequest;
import br.com.cvc.poc.rulescrud.application.PublishService;
import br.com.cvc.poc.rulescrud.infra.db.RulesetEntity;
import br.com.cvc.poc.rulescrud.infra.db.RulesetVersionEntity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.UUID;

@Path("/rulesets/{rulesetId}/publish")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PublishResource {

  @Inject PublishService publishService;

  @POST
  @Transactional
  public RulesetVersionEntity publish(@PathParam("rulesetId") UUID rulesetId, @Valid PublishRequest req) {
    var rs = RulesetEntity.findById(rulesetId);
    if (rs == null) throw new NotFoundException("ruleset not found");
    return publishService.publish(rulesetId, req.publishedBy());
  }
}
