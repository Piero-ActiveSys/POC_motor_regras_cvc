package br.com.cvc.poc.engine.index;

import br.com.cvc.poc.engine.model.Rule;
import br.com.cvc.poc.engine.runtime.PreparedItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MultiLevelIndex {

    private final Map<String, List<Rule>> index = new HashMap<>();

    public void addRule(Rule rule) {
        List<String> keys = buildKeys(rule);

        for (String key : keys) {
            index.computeIfAbsent(key, k -> new ArrayList<>()).add(rule);
        }
    }

    public List<Rule> findCandidates(PreparedItem item) {
        for (String key : item.indexKeys()) {
            List<Rule> rules = index.get(key);
            if (rules != null && !rules.isEmpty()) {
                return rules;
            }
        }

        List<Rule> global = index.get("GLOBAL");
        return global != null ? global : Collections.emptyList();
    }

    private List<String> buildKeys(Rule rule) {
        List<String> keys = new ArrayList<>();

        String broker = rule.get("broker");
        String cidade = rule.get("cidade");
        String estado = rule.get("estado");
        String hotelId = rule.get("hotelid");

        if (broker != null && cidade != null && estado != null && hotelId != null) {
            keys.add(key(broker, cidade, estado, hotelId));
        }

        if (broker != null && cidade != null && estado != null) {
            keys.add(key(broker, cidade, estado));
        }

        if (broker != null && cidade != null) {
            keys.add(key(broker, cidade));
        }

        if (broker != null) {
            keys.add(key(broker));
        }

        keys.add("GLOBAL");

        return keys;
    }

    private String key(String... parts) {
        return String.join("|", parts);
    }
}