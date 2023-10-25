package com.aquila.chess.strategy.mcts.inputs.aquila;

import com.aquila.chess.strategy.mcts.inputs.InputsFullNN;
import com.aquila.chess.strategy.mcts.inputs.InputsManager;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.Move;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static com.chess.engine.classic.Alliance.BLACK;
import static com.chess.engine.classic.Alliance.WHITE;

@Slf4j
class AquilaInputsManagerImplTest {

    /**
     * <pre>
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * 8  --- --- --- --- --- K-B --- ---  8
     * 7  P-W --- --- --- --- --- --- ---  7
     * 6  --- --- --- R-W --- K-W --- ---  6
     * 5  --- --- --- --- --- --- --- ---  5
     * 4  --- --- --- --- --- --- --- ---  4
     * 3  --- --- --- --- --- --- --- ---  3
     * 2  --- --- --- --- --- --- --- ---  2
     * 1  --- --- --- --- --- --- --- ---  1
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * </pre>
     */
    @Test
    void createInputs() {
        final Board board = Board.createBoard("pa7,rd6,kf6", "kf8,bb8,ph7", WHITE);
        InputsManager inputsManager = new AquilaInputsManagerImpl();
        InputsFullNN inputs = inputsManager.createInputs(board, null, new ArrayList<>(), BLACK);
        log.info("inputs:\n{}", inputs);

        long hash1 = inputsManager.hashCode(board, null, new ArrayList<>(), WHITE);
        long hash2 = inputsManager.hashCode(board, null, new ArrayList<>(), BLACK);
        long hash3 = inputsManager.hashCode(board, null, new ArrayList<>(), WHITE);
        log.info("hash1={}", hash1);
        log.info("hash2={}", hash2);
        log.info("hash3={}", hash3);
    }

}