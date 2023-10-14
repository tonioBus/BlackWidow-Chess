package com.aquila.chess.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.map.LRUMap;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class LruMapTest {

    LRUMap<Integer, String> lruMap = new LRUMap<>(5);

    @Test
    void testLRU() {
        put(1,"1");
        put(2,"2");
        put(3,"3");
        put(4,"4");
        lruMap.get(3);
        log.info("lru:{}", lruMap
                .entrySet()
                .stream()
                .map(entry-> String.format("[%d] -> %s", entry.getKey(), entry.getValue()))
                .collect(Collectors.joining("\n -"," -","\n")));
        put(5,"5");
        put(6,"6");
        put(7,"7");
        log.info("lru:{}", lruMap
                .entrySet()
                .stream()
                .map(entry-> String.format("[%d] -> %s", entry.getKey(), entry.getValue()))
                .collect(Collectors.joining("\n -"," -","\n")));
    }

    private void put(int key, String value) {
        if(lruMap.isFull()) {
            Map.Entry<Integer, String> entry = lruMap.entrySet().stream().findFirst().get();
            log.warn("we are about to remove:{} -> {}", entry.getKey(), entry.getValue());
        }
        lruMap.put(key, value);
    }
}
