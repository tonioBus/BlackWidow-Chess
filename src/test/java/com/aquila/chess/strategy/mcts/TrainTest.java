package com.aquila.chess.strategy.mcts;

import com.aquila.chess.Game;
import com.aquila.chess.TrainGame;
import com.aquila.chess.strategy.mcts.inputs.InputsManager;
import com.aquila.chess.strategy.mcts.inputs.aquila.AquilaInputsManagerImpl;
import com.aquila.chess.strategy.mcts.inputs.lc0.Lc0InputsManagerImpl;
import com.aquila.chess.strategy.mcts.nnImpls.NNSimul;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    InputsManager inputsManager = new Lc0InputsManagerImpl();
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

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8})
    void testTrain(int seed) throws Exception {
        List<Game> games = new ArrayList<>();
        List<Integer> savedGames = new ArrayList<>();
        final Board board = Board.createStandardBoard();
        final Game game = Game.builder().inputsManager(inputsManager).board(board).build();
        final TrainGame trainGame = new TrainGame();
        final MCTSStrategy whiteStrategy = new MCTSStrategy(
                game,
                Alliance.WHITE,
                deepLearningWhite,
                seed,
                updateCpuct,
                -1)
                .withTrainGame(trainGame)
                .withNbThread(-1)
                .withNbSearchCalls(3);
        // .withNbThread(1);
        final MCTSStrategy blackStrategy = new MCTSStrategy(
                game,
                Alliance.BLACK,
                deepLearningBlack,
                seed,
                updateCpuct,
                -1)
                .withTrainGame(trainGame)
                .withNbThread(-1)
                .withNbSearchCalls(2);
        game.setup(whiteStrategy, blackStrategy);
        Game.GameStatus gameStatus = null;
        do {
            gameStatus = game.play();
            if (gameStatus != Game.GameStatus.IN_PROGRESS) break;
            log.info(game.toString());
        } while (true);
        log.info("#########################################################################");
        log.info("END OF game :\n{}\n{}", gameStatus, game);
        log.info("#########################################################################");
        if (gameStatus != Game.GameStatus.DRAW_300 && game.getMoves().size() != trainGame.getOneStepRecordList().size()) {
            log.error("game moves:{} <-> {} train moves", game.getMoves().size(), trainGame.getOneStepRecordList().size());
            assertTrue(false);
        }
        final String filename = trainGame.saveBatch("train-test", gameStatus);
        final int num = Integer.valueOf(Paths.get(filename).getFileName().toString());
        TrainGame loadTrainGame = TrainGame.load("train-test", num);
        StatisticsFit statisticsFit = new StatisticsFit(seed, seed);
        try {
            deepLearningWhite.train(loadTrainGame, statisticsFit);
        } catch (IOException e) {
            log.info("Game:\n{}", game);
            assertFalse(true, "Exception:" + e);
        }
        log.info("statistics:{}", statisticsFit);
    }

    @Test
    void testLoad() throws IOException, ClassNotFoundException, TrainException {
        TrainGame trainGame = TrainGame.load("train-test-load", 1);
        StatisticsFit statisticsFit = new StatisticsFit(1,1);
        deepLearningWhite.train(trainGame, statisticsFit);
        log.info("statistics:{}", statisticsFit);
    }

    @Test
    @Disabled
    void testPunctualLoad() throws IOException, ClassNotFoundException, TrainException {
        TrainGame trainGame = TrainGame.load("train-aquila-grospc", 1060);
        StatisticsFit statisticsFit = new StatisticsFit(1060,1060);
        deepLearningWhite.train(trainGame, statisticsFit);
        log.info("statistics:{}", statisticsFit);
    }

    @AfterAll
    static void afterAll() throws IOException {
        File directory = new File("train-test");
        FileUtils.cleanDirectory(directory);
    }
}
