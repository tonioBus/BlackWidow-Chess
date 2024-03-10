package com.aquila.chess.strategy.mcts;

import com.aquila.chess.Game;
import com.aquila.chess.TrainGame;
import com.aquila.chess.strategy.mcts.inputs.lc0.Lc0InputsManagerImpl;
import com.aquila.chess.strategy.mcts.nnImpls.NNSimul;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.Move;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class TrainTest {

    private static final int FIT_CHUNK = 40;
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
    private Lc0InputsManagerImpl inputsManager;
    private DeepLearningAGZ deepLearningWhite, deepLearningBlack;

    @BeforeEach
    void beforeEach() {
        inputsManager = new Lc0InputsManagerImpl();
        deepLearningWhite = DeepLearningAGZ.builder()
                .nn(nnWhite)
                .inputsManager(inputsManager)
                .batchSize(12)
                .train(true)
                .build();
        deepLearningBlack = DeepLearningAGZ.builder()
                .nn(nnBlack)
                .inputsManager(inputsManager)
                .batchSize(12)
                .train(false)
                .build();
    }

    @AfterEach
    void tearDown() {

    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20})
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
                .withNbThread(1)
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
                .withNbThread(1)
                .withNbSearchCalls(2);
        game.setup(whiteStrategy, blackStrategy);
        Game.GameStatus gameStatus = null;
        do {
            List<Move> legalMoves = game.getNextPlayer().getLegalMoves();
            Move previousMove = game.getLastMove();
            gameStatus = game.play();
            Move lastMove = game.getLastMove();
            if (gameStatus.isTheEnd()) {
                game.end(lastMove);
                break;
            }
            if (legalMoves.stream().noneMatch(legalMove -> legalMove.toString().equals(lastMove.toString()))) {
                log.error("legalMoves:{}", legalMoves.stream().map(Object::toString).collect(Collectors.joining(",")));
                log.error("lastMove:{}", lastMove);
                fail();
            }
            String lastTrainMove = trainGame.getOneStepRecordList().getLast().move();
            if (!previousMove.toString().equals(lastTrainMove)) {
                log.error("previous move:{} <-> {}:train move", previousMove, lastTrainMove);
                fail();
            }
            log.info(game.toString());
        } while (true);
        log.info("#########################################################################");
        log.info("END OF game :\n{}\n{}", gameStatus, game);
        log.info("#########################################################################");
        if (gameStatus != Game.GameStatus.DRAW_TOO_MUCH_STEPS && game.getMoves().size() != trainGame.getOneStepRecordList().size()) {
            log.error("game moves:{} <-> {} train moves", game.getMoves().size(), trainGame.getOneStepRecordList().size());
            fail();
        }
        final String filename = trainGame.saveBatch("train-test", gameStatus);
        final int num = Integer.valueOf(Paths.get(filename).getFileName().toString());
        TrainGame loadTrainGame = TrainGame.load("train-test", num);
        StatisticsFit statisticsFit = new StatisticsFit(seed, seed);
        try {
            deepLearningWhite.train(loadTrainGame, FIT_CHUNK, statisticsFit);
        } catch (IOException e) {
            log.info("Game:\n{}", game);
            fail("Exception:" + e);
        }
        log.info("statistics:{}", statisticsFit);
    }

    @ParameterizedTest
    @ValueSource(strings = {"train-test-load/10"})
    void testLoad(String fileName) throws IOException, ClassNotFoundException, TrainException {
        TrainGame trainGame = TrainGame.load(new File(fileName));
        StatisticsFit statisticsFit = new StatisticsFit(1, 1);
        deepLearningWhite.train(trainGame, FIT_CHUNK, statisticsFit);
        log.info("statistics:{}", statisticsFit);
    }

    @Test
    @Disabled
    void testPunctualLoad() throws IOException, ClassNotFoundException, TrainException {
        TrainGame trainGame = TrainGame.load("train-aquila-grospc", 1060);
        StatisticsFit statisticsFit = new StatisticsFit(1060, 1060);
        deepLearningWhite.train(trainGame, FIT_CHUNK, statisticsFit);
        log.info("statistics:{}", statisticsFit);
    }

    @AfterAll
    static void afterAll() throws IOException {
        File directory = new File("train-test");
        FileUtils.cleanDirectory(directory);
    }
}
