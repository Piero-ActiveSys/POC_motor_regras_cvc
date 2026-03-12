package br.com.cvc.poc.engine.api;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/health")
public class HealthResource {
  @GET
  public String ok() { return "OK"; }
}
