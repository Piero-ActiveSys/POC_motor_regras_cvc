package br.com.cvc.poc.rulescrud.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class Json {
  @Inject ObjectMapper mapper;

  public String toJson(Object o) {
    try { return mapper.writeValueAsString(o); }
    catch (Exception e) { throw new RuntimeException(e); }
  }

  public <T> T fromJson(String s, Class<T> c) {
    try { return mapper.readValue(s, c); }
    catch (Exception e) { throw new RuntimeException(e); }
  }
}
