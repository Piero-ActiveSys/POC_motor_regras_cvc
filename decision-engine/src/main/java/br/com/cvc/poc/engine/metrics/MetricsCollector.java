package br.com.cvc.poc.engine.metrics;

import java.util.Locale;

public class MetricsCollector {

    public long requestPreparation;
    public long itemPreparation;
    public long indexLookup;
    public long ruleMatchingMarkup;
    public long ruleMatchingCommission;
    public long mergeResult;

    public void addRequestPreparationNanos(long nanos) {
        requestPreparation += nanos;
    }

    public void addItemPreparationNanos(long nanos) {
        itemPreparation += nanos;
    }

    public void addIndexLookupNanos(long nanos) {
        indexLookup += nanos;
    }

    public void addRuleMatchingMarkupNanos(long nanos) {
        ruleMatchingMarkup += nanos;
    }

    public void addRuleMatchingCommissionNanos(long nanos) {
        ruleMatchingCommission += nanos;
    }

    public void addMergeResultNanos(long nanos) {
        mergeResult += nanos;
    }

    public void mergeFrom(MetricsCollector other) {
        if (other == null) return;
        requestPreparation += other.requestPreparation;
        itemPreparation += other.itemPreparation;
        indexLookup += other.indexLookup;
        ruleMatchingMarkup += other.ruleMatchingMarkup;
        ruleMatchingCommission += other.ruleMatchingCommission;
        mergeResult += other.mergeResult;
    }

    public void printSummary() {
        System.out.println("====== ENGINE METRICS ======");
        System.out.println("requestPreparation: " + formatMillis(requestPreparation) + " ms");
        System.out.println("itemPreparation: " + formatMillis(itemPreparation) + " ms");
        System.out.println("indexLookup: " + formatMillis(indexLookup) + " ms");
        System.out.println("ruleMatchingMarkup: " + formatMillis(ruleMatchingMarkup) + " ms");
        System.out.println("ruleMatchingCommission: " + formatMillis(ruleMatchingCommission) + " ms");
        System.out.println("mergeResult: " + formatMillis(mergeResult) + " ms");
        System.out.println("============================");
    }

    private static String formatMillis(long nanos) {
        return String.format(Locale.US, "%.3f", nanos / 1_000_000.0d);
    }
}
