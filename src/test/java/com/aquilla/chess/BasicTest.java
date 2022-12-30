package com.aquilla.chess;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;

@Slf4j
public class BasicTest {

    @Test
    void testCircularQueue() {
        CircularFifoQueue<Integer> lastMoves = new CircularFifoQueue<>(8);
        for (int i = 0; i < 10; i++) lastMoves.add(i);
        log.info("lastMoves[0]:", lastMoves.get(0));
        String gameMoves = lastMoves.stream().map(move -> move.toString()).collect(Collectors.joining(","));
        log.info("gameMoves:{}", gameMoves);
    }

    @Test
    void testLimitedList() {


    }
}
