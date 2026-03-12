package br.com.cvc.poc.engine.api;

import br.com.cvc.poc.contracts.PricingRequest;
import br.com.cvc.poc.contracts.PricingResponse;
import br.com.cvc.poc.engine.application.PricingService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

@Path("/price")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PricingResource {

  @Inject PricingService service;

  private final ConcurrentHashMap<String, Job> jobs = new ConcurrentHashMap<>();

  @POST
  @Path("/calculate")
  public PricingResponse calculate(@Valid PricingRequest req) {
    return service.calculate(req);
  }

  @POST
  @Path("/jobs")
  public Map<String, String> createJob(@Valid PricingRequest req) {
    var id = UUID.randomUUID().toString();
    var f = CompletableFuture.supplyAsync(() -> service.calculate(req));
    jobs.put(id, new Job(f));
    return Map.of("jobId", id);
  }

  @GET
  @Path("/jobs/{jobId}")
  public Object job(@PathParam("jobId") String jobId) {
    var j = jobs.get(jobId);
    if (j == null) throw new NotFoundException("job not found");
    if (!j.future.isDone()) return Map.of("status","RUNNING");
    try {
      return j.future.get();
    } catch (Exception e) {
      return Map.of("status","FAILED","error", String.valueOf(e.getMessage()));
    }
  }

  private static class Job {
    final CompletableFuture<PricingResponse> future;
    Job(CompletableFuture<PricingResponse> f) { this.future = f; }
  }
}
