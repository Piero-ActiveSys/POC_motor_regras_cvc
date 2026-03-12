package br.com.cvc.poc.rulescrud.api;

import br.com.cvc.poc.contracts.RulesetCreateRequest;
import br.com.cvc.poc.rulescrud.infra.db.RulesetEntity;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.UUID;

@Path("/rulesets")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RulesetResource {

  @POST
  @Transactional
  public RulesetEntity create(@Valid RulesetCreateRequest req) {
    var e = RulesetEntity.create(req.name(), req.createdBy());
    e.persist();
    return e;
  }

  @GET
  @Path("/{id}")
  public RulesetEntity get(@PathParam("id") UUID id) {
    var e = RulesetEntity.findById(id);
    if (e == null) throw new NotFoundException("ruleset not found");
    return (RulesetEntity) e;
  }
}
