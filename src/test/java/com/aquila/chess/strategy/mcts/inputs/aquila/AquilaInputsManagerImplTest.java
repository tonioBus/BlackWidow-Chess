package com.aquila.chess.strategy.mcts.inputs.aquila;

import com.aquila.chess.strategy.mcts.inputs.InputsFullNN;
import com.aquila.chess.strategy.mcts.inputs.InputsManager;
import com.aquila.chess.strategy.mcts.inputs.lc0.Lc0InputsManagerImpl;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.pieces.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static com.chess.engine.classic.Alliance.BLACK;
import static com.chess.engine.classic.Alliance.WHITE;
import static org.junit.jupiter.api.Assertions.*;

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
     */@Test
    void createInputs() {
        final Board board = Board.createBoard("pa7,rd6,kf6", "kf8,bb8,ph7", WHITE);
        InputsManager inputsManager = new AquilaInputsManagerImpl();
        InputsFullNN inputs = inputsManager.createInputs(board, null, BLACK);
        log.info("inputs:\n{}", inputs);
    }

}