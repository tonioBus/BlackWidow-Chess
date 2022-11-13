package com.chess.engine.bitboards;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class BitBoardV2Test {

    @Test
    public void test() {
        BitBoardV2 v2 = BitBoardV2.standardBoard().calculateLegalMoves();
        log.info("v2:{}", v2);
    }

}