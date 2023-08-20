package com.aquila.chess.strategy.mcts;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.map.LRUMap;

import java.util.Collection;
import java.util.Map;

@Slf4j
public class CacheValues {

    private final Map<Long, CacheValue> lruMap;

    public Collection<CacheValue> getValues() {
        return lruMap.values();
    }

    public CacheValues(final int size) {
        lruMap = new LRUMap<>(size);
    }

    public synchronized void clearCache() {
        if (log.isDebugEnabled()) log.debug("EMPTY cacheNNValues: {}", this.lruMap.size());
        this.lruMap.clear();
    }

    public synchronized CacheValue get(final long key) {
        CacheValue ret = lruMap.get(key);
        return ret;
    }

    public synchronized boolean containsKey(final long key) {
        return this.lruMap.containsKey(key);
    }

    public synchronized int size() {
        return this.lruMap.size();
    }

    public static CacheValue createDummy(String label) {
        return CacheValue.getNotInitialized(label);
    }

    public synchronized CacheValue create(long key, final String label) {
        if (containsKey(key)) throw new RuntimeException("node already created for key:" + key);
        CacheValue ret = CacheValue.getNotInitialized(String.format("[%d] %s", key, label));
        this.lruMap.put(key, ret);
        return ret;
    }

    public synchronized CacheValue updateValueAndPolicies(long key, double value, double[] notNormalisedPolicies) {
        CacheValue cacheValue = this.lruMap.get(key);
        if (cacheValue == null) {
            throw new RuntimeException("node for key:" + key + " not found");
        }
        cacheValue.setTrueValuesAndPolicies(value, notNormalisedPolicies);
        return cacheValue;
    }

}
