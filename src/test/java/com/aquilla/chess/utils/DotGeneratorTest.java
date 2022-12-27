package com.aquilla.chess.utils;

import com.aquilla.chess.Game;
import com.aquilla.chess.strategy.RandomStrategy;
import com.aquilla.chess.strategy.mcts.*;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
class DotGeneratorTest {

    DeepLearningAGZ deepLearningWhite;
    DeepLearningAGZ deepLearningBlack;
    final UpdateCpuct updateCpuct = (nbStep) ->
    {
        return Math.exp(-0.04 * nbStep) / 2;
    };

    @BeforeEach
    public void initMockDeepLearning() {
        NNTest nn = new NNTest();
        deepLearningWhite = new DeepLearningAGZ(nn);
        deepLearningBlack = new DeepLearningAGZ(nn);
    }

    @RepeatedTest(1)
    void testGenerate() throws Exception {
        playMCTS(10);
    }

    @Test
    void testBegining() throws Exception {
        playMCTS(4);
    }

    void playMCTS(final int nbStepMax) throws Exception {
        final int seed = 1;
        final Board board = Board.createStandardBoard();
        final Game game = Game.builder().board(board).build();
        final MCTSStrategy whiteStrategy = new MCTSStrategy(
                game,
                Alliance.WHITE,
                deepLearningWhite,
                seed,
                updateCpuct,
                -1)
                .withNbThread(1)
                .withNbMaxSearchCalls(5);
        final RandomStrategy blackStrategy = new RandomStrategy(Alliance.BLACK, 1000);
        game.setup(whiteStrategy, blackStrategy);
        int nbStep = 0;
        Game.GameStatus gameStatus;
        do {
            if (nbStep++ > nbStepMax)
                break;
            gameStatus = game.play();
            assertTrue(game.getStrategyWhite() instanceof MCTSStrategy);
            assertTrue(game.getStrategyBlack() instanceof RandomStrategy);
            assertEquals(whiteStrategy, game.getStrategyWhite());
            assertEquals(blackStrategy, game.getStrategyBlack());
            assert(game.getStrategyWhite().getAlliance() ==  Alliance.WHITE);
            assert(game.getStrategyBlack().getAlliance() ==  Alliance.BLACK);
        } while (gameStatus == Game.GameStatus.IN_PROGRESS);
        log.info(game.toString());
        final MCTSNode node = whiteStrategy.getRoot();
        log.info("\n{}\n{}\n",
                "##########################################################################################################",
                DotGenerator.toString(node, 15));
    }

}