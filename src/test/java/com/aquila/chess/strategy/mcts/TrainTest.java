package com.aquila.chess.strategy.mcts;

import com.aquila.chess.Game;
import com.aquila.chess.TrainGame;
import com.aquila.chess.strategy.mcts.inputs.InputsManager;
import com.aquila.chess.strategy.mcts.inputs.aquila.AquilaInputsManagerImpl;
import com.aquila.chess.strategy.mcts.nnImpls.NNSimul;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.Move;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

@Slf4j
public class TrainTest {

    private static final UpdateCpuct updateCpuct = nbStep -> {
        if (nbStep <= 30) return 2.5;
        else return 0.0025;
    };

    INN nnWhite = new NNSimul(1) {
        @Override
        public void fit(double[][][][] inputs, double[][] policies, double[][] values) {

        }
    };
    INN nnBlack = new NNSimul(1) {
        @Override
        public void fit(double[][][][] inputs, double[][] policies, double[][] values) {

        }
    };
    InputsManager inputsManager = new AquilaInputsManagerImpl();
    final DeepLearningAGZ deepLearningWhite = DeepLearningAGZ.builder()
            .nn(nnWhite)
            .inputsManager(inputsManager)
            .batchSize(128)
            .train(true)
            .build();
    final DeepLearningAGZ deepLearningBlack = DeepLearningAGZ.builder()
            .nn(nnBlack)
            .inputsManager(inputsManager)
            .batchSize(128)
            .train(false)
            .build();

    @AfterEach
    void tearDown() {

    }

    @Test
    void testTrain() throws Exception {
        final Board board = Board.createStandardBoard();
        final Game game = Game.builder().inputsManager(inputsManager).board(board).build();
        long seed = 314;
        final MCTSStrategy whiteStrategy = new MCTSStrategy(
                game,
                Alliance.WHITE,
                deepLearningWhite,
                seed,
                updateCpuct,
                -1)
                .withNbThread(1)
                .withNbSearchCalls(1);
        // .withNbThread(1);
        final MCTSStrategy blackStrategy = new MCTSStrategy(
                game,
                Alliance.BLACK,
                deepLearningBlack,
                seed,
                updateCpuct,
                -1)
                .withNbThread(6)
                .withNbSearchCalls(400);
        whiteStrategy.setPartnerStrategy(blackStrategy);
        game.setup(whiteStrategy, blackStrategy);
        Game.GameStatus gameStatus = null;
        int i = 0;
        do {
            gameStatus = game.play();
            if (gameStatus != Game.GameStatus.IN_PROGRESS) break;
            Move move = game.getLastMove();
            log.info("[{}]: move:{}", move.getAllegiance(), move);
        } while (true);
        log.info("#########################################################################");
        log.info("END OF game :\n{}\n{}", gameStatus, game);
        log.info("#########################################################################");
        ResultGame resultGame = new ResultGame(0, 1);
        final String filename = whiteStrategy.saveBatch("train-test", resultGame);
        int num = Integer.valueOf(Paths.get(filename).getFileName().toString());
        TrainGame trainGame = TrainGame.load("train-test", num);
        deepLearningWhite.train(trainGame);
    }

    @Test
    void testLoad() throws IOException, ClassNotFoundException {
        TrainGame trainGame = TrainGame.load("train-test-load", 1);
        deepLearningWhite.train(trainGame);
    }

    @AfterAll
    static void afterAll() throws IOException {
        File directory = new File("train-test");
        FileUtils.cleanDirectory(directory);
    }
}
