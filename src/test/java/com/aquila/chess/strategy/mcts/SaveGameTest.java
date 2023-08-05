package com.aquila.chess.strategy.mcts;

import com.aquila.chess.Game;
import com.aquila.chess.TrainGame;
import com.aquila.chess.UtilsTest;
import com.aquila.chess.manager.GameManager;
import com.aquila.chess.manager.Sequence;
import com.aquila.chess.strategy.mcts.inputs.InputsManager;
import com.aquila.chess.strategy.mcts.inputs.aquila.AquilaInputsManagerImpl;
import com.aquila.chess.strategy.mcts.inputs.lc0.Lc0InputsManagerImpl;
import com.aquila.chess.strategy.mcts.nnImpls.NNSimul;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.Move;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;

import static com.chess.engine.classic.Alliance.WHITE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class SaveGameTest {

    static public final int NB_STEP = 2;

    private static final UpdateCpuct updateCpuct = nbStep -> {
        if (nbStep <= 30) return 2.5;
        else return 0.0025;
    };

    private static final Dirichlet dirichlet = nbStep -> false; // nbStep <= 30;

    @ParameterizedTest
    @ValueSource(ints = {3, 6, 12})
    @Order(0)
    void testSaveGameLc0(int nbStep) throws Exception {
        GameManager gameManager = new GameManager("sequences-todel.csv", 40, 55);
        INN nnWhite = new NNSimul(1);
        INN nnBlack = new NNSimul(1);
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

        final Board board = Board.createStandardBoard();
        final Game game = Game.builder().board(board).build();
        Sequence sequence = gameManager.createSequence();
        long seed = 314;
        final MCTSStrategy whiteStrategy = new MCTSStrategy(game, Alliance.WHITE, deepLearningWhite, seed, updateCpuct, -1).withNbThread(1).withNbSearchCalls(NB_STEP).withDirichlet(dirichlet);
        final MCTSStrategy blackStrategy = new MCTSStrategy(game, Alliance.BLACK, deepLearningBlack, seed, updateCpuct, -1).withNbThread(1).withNbSearchCalls(NB_STEP).withDirichlet(dirichlet);
        whiteStrategy.setPartnerStrategy(blackStrategy);
        game.setup(whiteStrategy, blackStrategy);
        Game.GameStatus gameStatus = null;
        for (int i = 0; i < nbStep; i++) {
            gameStatus = game.play();
            if(gameStatus != Game.GameStatus.IN_PROGRESS) break;
            sequence.play();
            assertTrue(UtilsTest.verify8inputs((Lc0InputsManagerImpl) game.getInputsManager()));
            log.info("####################################################");
            log.info("game step[{}] :\n{}", i, game);
            whiteStrategy.getTrainGame().getOneStepRecordList().forEach(oneStepRecord -> log.info("TRAIN STEP {}-{}\n{}", oneStepRecord.color2play(), oneStepRecord.move(), oneStepRecord));
        }
        log.info("#########################################################################");
        log.info("END OF game [{}] :\n{}\n{}", gameManager.getNbGames(), gameStatus, game);
        log.info("#########################################################################");
        ResultGame resultGame = new ResultGame(1, 1);
        whiteStrategy.saveBatch("train-test", resultGame, -666);
        TrainGame trainGame = TrainGame.load("train-test", -666);
        // we play 5 times + the first position:
        // 0) Initial,  1) First move, etc ...
        assertEquals(nbStep - 1, trainGame.getOneStepRecordList().size());
        trainGame.getOneStepRecordList().forEach(oneStepRecord -> {
            log.info("inputs.size:{}", oneStepRecord.inputs().inputs().length);
            log.info("move:{}", oneStepRecord.move());
            log.info("policies.size:{}", oneStepRecord.policies().size());
            log.info("color2play:{}", oneStepRecord.color2play());
            log.info("----------------------------------------");
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {6, 12, 30})
    @Order(0)
    void testSaveGameAquila(int nbStep) throws Exception {
        GameManager gameManager = new GameManager("sequences-todel.csv", 40, 55);
        INN nnWhite = new NNSimul(1);
        INN nnBlack = new NNSimul(1);
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

        final Board board = Board.createStandardBoard();
        final Game game = Game.builder().inputsManager(inputsManager).board(board).build();
        Sequence sequence = gameManager.createSequence();
        long seed = 314;
        final MCTSStrategy whiteStrategy = new MCTSStrategy(game, Alliance.WHITE, deepLearningWhite, seed, updateCpuct, -1).withNbThread(1).withNbSearchCalls(NB_STEP).withDirichlet(dirichlet);
        final MCTSStrategy blackStrategy = new MCTSStrategy(game, Alliance.BLACK, deepLearningBlack, seed, updateCpuct, -1).withNbThread(1).withNbSearchCalls(NB_STEP).withDirichlet(dirichlet);
        whiteStrategy.setPartnerStrategy(blackStrategy);
        game.setup(whiteStrategy, blackStrategy);
        Game.GameStatus gameStatus = null;
        for (int i = 0; i < nbStep; i++) {
            gameStatus = game.play();
            if(gameStatus != Game.GameStatus.IN_PROGRESS) break;
            sequence.play();
            log.info("####################################################");
            log.info("game step[{}] :\n{}", i, game);
            whiteStrategy.getTrainGame().getOneStepRecordList().forEach(oneStepRecord -> log.info("TRAIN STEP {}-{}\n{}", oneStepRecord.color2play(), oneStepRecord.move(), oneStepRecord));
        }
        log.info("#########################################################################");
        log.info("END OF game [{}] :\n{}\n{}", gameManager.getNbGames(), gameStatus, game);
        log.info("#########################################################################");
        ResultGame resultGame = new ResultGame(1, 1);
        whiteStrategy.saveBatch("train", resultGame, -666);
        TrainGame trainGame = TrainGame.load("train", -666);
        // we play 5 times + the first position:
        // 0) Initial,  1) First move, etc ...
        assertEquals(sequence.nbStep - 1, trainGame.getOneStepRecordList().size());
        trainGame.getOneStepRecordList().forEach(oneStepRecord -> {
            log.info("inputs.size:{}", oneStepRecord.inputs().inputs().length);
            log.info("move:{}", oneStepRecord.move());
            log.info("policies.size:{}", oneStepRecord.policies().size());
            log.info("color2play:{}", oneStepRecord.color2play());
            log.info("----------------------------------------");
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5, 6})
    @Order(0)
    void testIntermediateSaveGame(int nbStep) throws Exception {
        GameManager gameManager = new GameManager("sequences-todel.csv", 40, 55);
        INN nnWhite = new NNSimul(1);
        INN nnBlack = new NNSimul(1);
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
        final Board board = Board.createBoard("kg1", "pe3,kg3", WHITE);
        final Game game = Game.builder().inputsManager(inputsManager).board(board).build();
        Sequence sequence = gameManager.createSequence();
        long seed = 314;
        final MCTSStrategy whiteStrategy = new MCTSStrategy(game, Alliance.WHITE, deepLearningWhite, seed, updateCpuct, -1).withNbThread(1).withNbSearchCalls(NB_STEP).withDirichlet(dirichlet);
        // .withNbThread(1);
        final MCTSStrategy blackStrategy = new MCTSStrategy(game, Alliance.BLACK, deepLearningBlack, seed, updateCpuct, -1).withNbThread(1).withNbSearchCalls(NB_STEP).withDirichlet(dirichlet);
        whiteStrategy.setPartnerStrategy(blackStrategy);
        game.setup(whiteStrategy, blackStrategy);
        Game.GameStatus gameStatus = null;
        for (int i = 0; i < nbStep; i++) {
            log.info("PLAYER:{}", game.getColor2play());
            log.info("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&");
            gameStatus = game.play();
            assertTrue(UtilsTest.verify8inputs((Lc0InputsManagerImpl) game.getInputsManager()));
            Move move = game.getLastMove();
            log.info("####################################################");
            log.info("[{}]: move:{}", move.getAllegiance(), move);
            sequence.play();
            log.info("----------------------------------------------------");
            log.info("game step[{}] :\n{}", i, game);
            whiteStrategy.getTrainGame().getOneStepRecordList().forEach(oneStepRecord -> log.info("TRAIN STEP {}-{}\n{}", oneStepRecord.color2play(), oneStepRecord.move(), oneStepRecord));
        }
        assertEquals(nbStep - ((nbStep & 0x01) == 0 ? 1 : 0), whiteStrategy.getTrainGame().getOneStepRecordList().size());
        log.info("#########################################################################");
        log.info("END OF game [{}] :\n{}\n{}", gameManager.getNbGames(), gameStatus, game);
        log.info("#########################################################################");
        ResultGame resultGame = new ResultGame(1, 1);
        whiteStrategy.saveBatch("train-test", resultGame, -666);
        TrainGame trainGame = TrainGame.load("train-test", -666);
        // we play 5 times + the first position:
        // 0) Initial,  1) First move, etc ...
        assertEquals(nbStep - ((nbStep & 0x01) == 0 ? 1 : 0) , trainGame.getOneStepRecordList().size());
    }

    @Test
    @Order(1)
    public void testLoadTraining() throws IOException, ClassNotFoundException {
        TrainGame trainGame = TrainGame.load("train", -666);
        trainGame.getOneStepRecordList().forEach(oneStepRecord -> log.info("board(0):\n{}", oneStepRecord));
    }

}
