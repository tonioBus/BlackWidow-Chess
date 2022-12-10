package com.aquila.chess;

import com.chess.engine.classic.board.Move;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.junit.jupiter.api.Test;

@Slf4j
public class BasicTest {

    @Test
    void testCircularQueue() {
        CircularFifoQueue<Integer> lastMoves = new CircularFifoQueue<>(8);
        for(int i=0; i<10; i++) lastMoves.add(i);
        log.info("lastMoves[0]:", lastMoves.get(0));
    }
}
