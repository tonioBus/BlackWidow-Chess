package com.aquila.chess.strategy.mcts;

import com.aquila.chess.Game;
import com.aquila.chess.TrainGame;
import com.aquila.chess.manager.GameManager;
import com.aquila.chess.manager.Sequence;
import com.aquila.chess.strategy.mcts.nnImpls.NNSimul;
import com.aquila.chess.utils.Utils;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
public class SaveGameTest {

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
    @Order(0)
    void testSaveGame() throws Exception {
        GameManager gameManager = new GameManager("sequences-todel.csv", 40, 55);
        INN nnWhite = new NNSimul(1);
        INN nnBlack = new NNSimul(1);
        DeepLearningAGZ deepLearningWhite = new DeepLearningAGZ(nnWhite, true);
        DeepLearningAGZ deepLearningBlack = new DeepLearningAGZ(nnBlack, false);
        final Board board = Board.createStandardBoard();
        final Game game = Game.builder().board(board).build();
        Sequence sequence = gameManager.createSequence();
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
        for (int i = 0; i < 5; i++) {
            gameStatus = game.play();
            sequence.play();
            log.info("game:\n{}", game);
        }
        log.info("#########################################################################");
        log.info("END OF game [{}] :\n{}\n{}", gameManager.getNbGames(), gameStatus, game);
        log.info("#########################################################################");
        ResultGame resultGame = new ResultGame(1, 1);
        whiteStrategy.saveBatch(resultGame, 666);
        TrainGame trainGame = TrainGame.load(666);
        // we play 5 times + the first position:
        // 0) Initial,  1) First move, etc ...
        assertEquals(6, trainGame.getOneStepRecordList().size());
    }

    @Test
    @Order(1)
    public void testLoadTraining() throws IOException, ClassNotFoundException {
        TrainGame trainGame = TrainGame.load(666);
        trainGame.getOneStepRecordList().forEach(oneStepRecord -> {
            log.info("board(0):\n{}", oneStepRecord);
        });
    }

}
