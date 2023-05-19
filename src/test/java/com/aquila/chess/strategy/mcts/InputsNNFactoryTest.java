package com.aquila.chess.strategy.mcts;

import com.aquila.chess.Game;
import com.aquila.chess.strategy.mcts.inputs.lc0.InputsNNFactory;
import com.aquila.chess.strategy.mcts.inputs.lc0.InputsOneNN;
import com.aquila.chess.strategy.mcts.nnImpls.NNSimul;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
class InputsNNFactoryTest {

    static public final int NB_STEP = 50;

    /**
     * The learning rate was set to 0.2 and dropped to 0.02, 0.002,
     * and 0.0002 after 100, 300, and 500 thousand steps for chess
     */
    public static final UpdateLr updateLr = nbGames -> {
        if (nbGames > 500000) return 1e-6;
        if (nbGames > 300000) return 1e-5;
        if (nbGames > 100000) return 1e-4;
        return 1e-3;
    };

    private static final UpdateCpuct updateCpuct = nbStep -> {
        if (nbStep <= 30) return 2.5;
        else return 0.0025;
    };

    private static final Dirichlet dirichlet = nbStep -> false; // nbStep <= 30;

    @Test
    void createInputsForOnePosition() {
        INN nnWhite = new NNSimul(1);
        INN nnBlack = new NNSimul(1);
        DeepLearningAGZ deepLearningWhite = new DeepLearningAGZ(nnWhite, true);
        DeepLearningAGZ deepLearningBlack = new DeepLearningAGZ(nnBlack, false);
        final Board board = Board.createStandardBoard();
        final Game game = Game.builder().board(board).build();
        long seed = 314;
        final MCTSStrategy whiteStrategy = new MCTSStrategy(
                game,
                Alliance.WHITE,
                deepLearningWhite,
                seed,
                updateCpuct,
                -1)
                .withNbSearchCalls(NB_STEP)
                .withDirichlet(dirichlet);
        // .withNbThread(1);
        final MCTSStrategy blackStrategy = new MCTSStrategy(
                game,
                Alliance.BLACK,
                deepLearningBlack,
                seed,
                updateCpuct,
                -1)
                .withNbSearchCalls(NB_STEP)
                .withDirichlet(dirichlet);
        whiteStrategy.setPartnerStrategy(blackStrategy);
        game.setup(whiteStrategy, blackStrategy);
        Game.GameStatus gameStatus = null;
        InputsOneNN inputs = InputsNNFactory.createInputsForOnePosition(board, null);
        String boartdSz = inputs.toString();
        log.info("board.string:\n{}", board.toString());
        assertEquals(board.toString(), boartdSz);
        log.info("BOARD:[{}]", boartdSz);
    }
}