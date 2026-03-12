package br.com.cvc.poc.engine.runtime;

import org.kie.api.KieServices;
import org.kie.api.builder.Message;
import org.kie.api.builder.Results;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

import java.util.List;

public final class DroolsCompiler {
  private DroolsCompiler() {}

  public static Compiled compile(String drl) {
    var ks = KieServices.Factory.get();
    var kfs = ks.newKieFileSystem();
    kfs.write("src/main/resources/rules.drl", drl);

    var kb = ks.newKieBuilder(kfs).buildAll();
    Results res = kb.getResults();
    if (res.hasMessages(Message.Level.ERROR)) {
      var errs = res.getMessages(Message.Level.ERROR).stream().map(Message::getText).toList();
      throw new IllegalArgumentException("DRL compile error: " + errs);
    }

    KieContainer kc = ks.newKieContainer(ks.getRepository().getDefaultReleaseId());
    return new Compiled(kc);
  }

  public record Compiled(KieContainer container) {
    public KieSession newSession() { return container.newKieSession(); }
  }
}
