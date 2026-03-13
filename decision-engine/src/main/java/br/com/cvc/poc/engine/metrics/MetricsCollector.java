package br.com.cvc.poc.engine.metrics;

import io.quarkus.logging.Log;

import java.util.Locale;

public class MetricsCollector {

    public long requestPreparation;
    public long itemPreparation;
    public long indexLookup;
    public long ruleMatchingMarkup;
    public long ruleMatchingCommission;
    public long mergeResult;

    // Request-scoped cache metrics
    public int cacheHits;
    public int cacheSize;

    // Activation pipeline metrics
    public long manifestLoadNanos;
    public long runtimeParseNanos;
    public long drlCompileNanos;
    public long snapshotBuildNanos;
    public long swapNanos;

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

    public void addCacheHits(int hits) { cacheHits += hits; }
    public void addCacheSize(int size) { cacheSize += size; }

    public void addManifestLoadNanos(long nanos) { manifestLoadNanos += nanos; }
    public void addRuntimeParseNanos(long nanos) { runtimeParseNanos += nanos; }
    public void addDrlCompileNanos(long nanos) { drlCompileNanos += nanos; }
    public void addSnapshotBuildNanos(long nanos) { snapshotBuildNanos += nanos; }
    public void addSwapNanos(long nanos) { swapNanos += nanos; }

    public void mergeFrom(MetricsCollector other) {
        if (other == null) return;
        requestPreparation += other.requestPreparation;
        itemPreparation += other.itemPreparation;
        indexLookup += other.indexLookup;
        ruleMatchingMarkup += other.ruleMatchingMarkup;
        ruleMatchingCommission += other.ruleMatchingCommission;
        mergeResult += other.mergeResult;
        cacheHits += other.cacheHits;
        cacheSize += other.cacheSize;
        manifestLoadNanos += other.manifestLoadNanos;
        runtimeParseNanos += other.runtimeParseNanos;
        drlCompileNanos += other.drlCompileNanos;
        snapshotBuildNanos += other.snapshotBuildNanos;
        swapNanos += other.swapNanos;
    }

    public void printSummary() {
        Log.infof("====== ENGINE METRICS ======");
        Log.infof("requestPreparation: %s ms", formatMillis(requestPreparation));
        Log.infof("itemPreparation: %s ms", formatMillis(itemPreparation));
        Log.infof("indexLookup: %s ms", formatMillis(indexLookup));
        Log.infof("ruleMatchingMarkup: %s ms", formatMillis(ruleMatchingMarkup));
        Log.infof("ruleMatchingCommission: %s ms", formatMillis(ruleMatchingCommission));
        Log.infof("mergeResult: %s ms", formatMillis(mergeResult));
        if (cacheHits > 0 || cacheSize > 0) {
            Log.infof("cacheHits: %d (distinct keys: %d)", cacheHits, cacheSize);
        }
        if (manifestLoadNanos > 0) Log.infof("manifestLoad: %s ms", formatMillis(manifestLoadNanos));
        if (runtimeParseNanos > 0) Log.infof("runtimeParse: %s ms", formatMillis(runtimeParseNanos));
        if (drlCompileNanos > 0) Log.infof("drlCompile: %s ms", formatMillis(drlCompileNanos));
        if (snapshotBuildNanos > 0) Log.infof("snapshotBuild: %s ms", formatMillis(snapshotBuildNanos));
        if (swapNanos > 0) Log.infof("swap: %s ms", formatMillis(swapNanos));
        Log.infof("============================");
    }

    private static String formatMillis(long nanos) {
        return String.format(Locale.US, "%.3f", nanos / 1_000_000.0d);
    }
}
