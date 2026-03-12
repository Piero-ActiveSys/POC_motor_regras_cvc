package br.com.cvc.poc.rulescrud.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class Checksum {
  private Checksum() {}
  public static String sha256(String content) {
    try {
      var md = MessageDigest.getInstance("SHA-256");
      var bytes = md.digest(content.getBytes(StandardCharsets.UTF_8));
      var sb = new StringBuilder();
      for (byte b : bytes) sb.append(String.format("%02x", b));
      return "sha256:" + sb;
    } catch (Exception e) { throw new RuntimeException(e); }
  }
}
