package com.chess.engine.bitboards;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class BitBoardTest {
    @Test
    public void test() {
        BitBoard board = new BitBoard();
        board.getBoardLegalMoves().forEach(m-> {
            log.info("Move:{}", m.toString());

        });

    }
}