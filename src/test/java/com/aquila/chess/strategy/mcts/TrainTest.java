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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

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
        List<Game> games = new ArrayList<>();
        List<Integer> savedGames = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final Board board = Board.createStandardBoard();
            final Game game = Game.builder().inputsManager(inputsManager).board(board).build();
            final TrainGame trainGame = new TrainGame();
            long seed = 1;
            final MCTSStrategy whiteStrategy = new MCTSStrategy(
                    game,
                    Alliance.WHITE,
                    deepLearningWhite,
                    seed,
                    updateCpuct,
                    -1)
                    .withTrainGame(trainGame)
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
                    .withTrainGame(trainGame)
                    .withNbThread(-1)
                    .withNbSearchCalls(100);
            whiteStrategy.setPartnerStrategy(blackStrategy);
            game.setup(whiteStrategy, blackStrategy);
            Game.GameStatus gameStatus = null;
            Move previousMove = null;
            do {
                gameStatus = game.play();
                if (gameStatus != Game.GameStatus.IN_PROGRESS) break;
                final Move move = game.getLastMove();
                String trainMoveSz = trainGame.getOneStepRecordList().getLast().move();
                if (previousMove != null && !trainMoveSz.equals(previousMove.toString())) {
                    log.error("[{}]: move:{} previousMove:{} trainMove:{}",
                            move.getAllegiance(),
                            move,
                            previousMove,
                            trainMoveSz);
                    assertTrue(false);
                }
                previousMove = move;
            } while (true);
            log.info("#########################################################################");
            log.info("END OF game :\n{}\n{}", gameStatus, game);
            log.info("#########################################################################");
            // 1 + nbStep ==> INIT_MOVE + nb steps
            if (gameStatus == Game.GameStatus.DRAW_300)
                assertEquals(game.getMoves().size() + 1, trainGame.getOneStepRecordList().size());
            else
                assertEquals(game.getMoves().size(), trainGame.getOneStepRecordList().size());
            final String filename = trainGame.saveBatch("train-test", gameStatus);
            final int num = Integer.valueOf(Paths.get(filename).getFileName().toString());
            savedGames.add(num);
            games.add(game);
        }
        log.info("Games:\n{}", games.stream().map(game -> game.toString()).collect(Collectors.joining("\n")));
        for (int i = 0; i < savedGames.size(); i++) {
            int num = savedGames.get(i);
            Game game = games.get(i);
            game.playAll();
            TrainGame loadTrainGame = TrainGame.load("train-test", num);
            try {
                deepLearningWhite.train(loadTrainGame);
            } catch (IOException e) {
                log.info("Game:\n{}", game);
                assertFalse(true, "Exception:" + e);
            }
        }
    }

    @Test
    void testLoad() throws IOException, ClassNotFoundException {
        TrainGame trainGame = TrainGame.load("train-test-load", 1);
        deepLearningWhite.train(trainGame);
    }

    @Test
    void testPunctualLoad() throws IOException, ClassNotFoundException {
        TrainGame trainGame = TrainGame.load("train-aquila-grospc", 654);
        deepLearningWhite.train(trainGame);
    }

    @AfterAll
    static void afterAll() throws IOException {
        File directory = new File("train-test");
        FileUtils.cleanDirectory(directory);
    }
}
