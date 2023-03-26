package com.aquila.chess.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.junit.jupiter.api.Test;

@Slf4j
public class CircularQueueTest {

    @Test
    void test() {
        CircularFifoQueue<Integer> queue = new CircularFifoQueue<>(5);
        queue.add(0);
        queue.add(1);
        queue.add(2);
        queue.add(3);
        queue.add(4);
        queue.add(5);
        log.info("peek:{}", queue.peek());
        log.info("element:{}", queue.element());
        log.info("poll:{}", queue.poll());
        int size = queue.size();
        log.info("get(max-1):{}", queue.get(queue.size() - 1));
    }
}
